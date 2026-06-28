package com.menulens.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.menulens.app.data.EntitlementState
import com.menulens.app.data.EntitlementStore
import com.menulens.app.data.RevealedHistoryStore
import com.menulens.app.data.ScanRepository
import com.menulens.app.model.MenuItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ScanPhase {
    IDLE,
    LOADING,
    SUCCESS,
    ERROR
}

sealed interface DishImageState {
    data object NotRequested : DishImageState
    data object Loading : DishImageState
    data class Ready(val localPath: String) : DishImageState
    data class Failed(val message: String) : DishImageState
}

data class ResultsUiState(
    val items: List<MenuItem> = emptyList(),
    val creditsRemainingToday: Int = EntitlementStore.DEFAULT_DAILY_CREDITS,
    val isPro: Boolean = false,
    val unlockedItemIds: Set<String> = emptySet(),
    val imageStates: Map<String, DishImageState> = emptyMap(),
    val scanPhase: ScanPhase = ScanPhase.IDLE,
    val scanErrorMessage: String? = null
) {
    fun itemById(itemId: String): MenuItem? = items.firstOrNull { it.itemId == itemId }
    fun isUnlocked(itemId: String): Boolean = isPro || unlockedItemIds.contains(itemId)
    fun imageState(itemId: String): DishImageState = imageStates[itemId] ?: DishImageState.NotRequested
}

sealed interface ResultsNavEvent {
    data class OpenDetail(val itemId: String) : ResultsNavEvent
    data class OpenShowToStaff(val itemId: String) : ResultsNavEvent
    data object OpenPaywall : ResultsNavEvent
}

class ResultsViewModel(application: Application) : AndroidViewModel(application) {
    private val entitlementStore = EntitlementStore(application.applicationContext)
    private val scanRepository = ScanRepository(application.applicationContext)
    private val revealedHistoryStore = RevealedHistoryStore(application.applicationContext)

    private val unlockedItemIds = MutableStateFlow<Set<String>>(emptySet())
    private val imageStates = MutableStateFlow<Map<String, DishImageState>>(emptyMap())
    private val scannedItems = MutableStateFlow<List<MenuItem>>(emptyList())
    private val scanPhase = MutableStateFlow(ScanPhase.IDLE)
    private val scanErrorMessage = MutableStateFlow<String?>(null)
    private val revealMutex = Mutex()
    private var pendingImageBytes: ByteArray? = null

    private val entitlementState = entitlementStore.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntitlementState(3, false))

    private val coreUiState = combine(
        entitlementState,
        unlockedItemIds,
        scannedItems,
        scanPhase,
        scanErrorMessage
    ) { entitlement, unlocked, items, phase, error ->
        ResultsUiState(
            items = items,
            creditsRemainingToday = entitlement.creditsRemainingToday,
            isPro = entitlement.isPro,
            unlockedItemIds = unlocked,
            scanPhase = phase,
            scanErrorMessage = error
        )
    }

    val uiState: StateFlow<ResultsUiState> = combine(
        coreUiState,
        imageStates
    ) { state, images ->
        state.copy(imageStates = images)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ResultsUiState()
    )

    private val _navEvents = MutableSharedFlow<ResultsNavEvent>(extraBufferCapacity = 8)
    val navEvents = _navEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            entitlementStore.refreshCreditsIfNeeded()
        }
    }

    fun startNewScanSession() {
        viewModelScope.launch {
            entitlementStore.refreshCreditsIfNeeded()
            unlockedItemIds.value = emptySet()
            imageStates.value = emptyMap()
            scannedItems.value = emptyList()
            scanPhase.value = ScanPhase.IDLE
            scanErrorMessage.value = null
        }
    }

    fun queueScanImage(imageBytes: ByteArray) {
        pendingImageBytes = imageBytes
        scanPhase.value = ScanPhase.IDLE
        scanErrorMessage.value = null
        scannedItems.value = emptyList()
        unlockedItemIds.value = emptySet()
        imageStates.value = emptyMap()
    }

    fun processPendingScan() {
        if (scanPhase.value == ScanPhase.LOADING) return
        val imageBytes = pendingImageBytes
        if (imageBytes == null) {
            scanPhase.value = ScanPhase.ERROR
            scanErrorMessage.value = "No image selected. Please scan or upload a menu photo."
            return
        }

        viewModelScope.launch {
            scanPhase.value = ScanPhase.LOADING
            scanErrorMessage.value = null
            try {
                val items = scanRepository.scanMenu(imageBytes)
                scannedItems.value = items
                imageStates.value = items.associate { item ->
                    val cached = scanRepository.cachedDishImagePath(item.itemId)
                    item.itemId to if (cached != null) {
                        DishImageState.Ready(cached)
                    } else {
                        DishImageState.NotRequested
                    }
                }
                scanPhase.value = ScanPhase.SUCCESS
                pendingImageBytes = null
            } catch (exc: Exception) {
                scanPhase.value = ScanPhase.ERROR
                scanErrorMessage.value = "Scan failed: ${exc.message ?: "Unknown error"}"
            }
        }
    }

    fun onResultItemSelected(itemId: String) {
        viewModelScope.launch {
            revealMutex.withLock {
                handleRevealOrNavigate(itemId)
            }
        }
    }

    fun onRevealFromDetail(itemId: String) {
        viewModelScope.launch {
            revealMutex.withLock {
                handleRevealOrNavigate(itemId)
            }
        }
    }

    fun onShowToStaff(itemId: String) {
        _navEvents.tryEmit(ResultsNavEvent.OpenShowToStaff(itemId))
    }

    fun retryDishImage(itemId: String) {
        requestDishImageIfNeeded(itemId, force = true)
    }

    fun setProEnabled(enabled: Boolean) {
        viewModelScope.launch {
            entitlementStore.setProEnabled(enabled)
        }
    }

    fun restorePurchasesStub() {
        viewModelScope.launch {
            entitlementStore.resetProForRestoreStub()
        }
    }

    private suspend fun handleRevealOrNavigate(itemId: String) {
        val current = uiState.value
        when (
            RevealPolicy.decide(
                isUnlocked = unlockedItemIds.value.contains(itemId),
                isPro = current.isPro,
                creditsRemainingToday = current.creditsRemainingToday
            )
        ) {
            RevealDecision.OPEN_ALREADY_UNLOCKED -> {
                _navEvents.emit(ResultsNavEvent.OpenDetail(itemId))
                requestDishImageIfNeeded(itemId)
            }

            RevealDecision.OPEN_WITH_PRO -> {
                unlockedItemIds.update { it + itemId }
                saveRevealedDish(itemId)
                _navEvents.emit(ResultsNavEvent.OpenDetail(itemId))
                requestDishImageIfNeeded(itemId)
            }

            RevealDecision.CONSUME_CREDIT_AND_OPEN -> {
                val consumed = entitlementStore.tryConsumeCredit()
                if (consumed) {
                    unlockedItemIds.update { it + itemId }
                    saveRevealedDish(itemId)
                    _navEvents.emit(ResultsNavEvent.OpenDetail(itemId))
                    requestDishImageIfNeeded(itemId)
                } else {
                    _navEvents.emit(ResultsNavEvent.OpenPaywall)
                }
            }

            RevealDecision.OPEN_PAYWALL -> {
                _navEvents.emit(ResultsNavEvent.OpenPaywall)
            }
        }
    }

    private fun requestDishImageIfNeeded(itemId: String, force: Boolean = false) {
        val item = scannedItems.value.firstOrNull { it.itemId == itemId } ?: return
        val token = item.imageGenerationToken ?: return
        val currentState = imageStates.value[itemId] ?: DishImageState.NotRequested
        val shouldRequest = DishImageRequestPolicy.shouldRequest(
            unlocked = uiState.value.isPro || unlockedItemIds.value.contains(itemId),
            state = currentState,
            forceRetry = force
        )
        if (!shouldRequest) return

        imageStates.update { it + (itemId to DishImageState.Loading) }
        viewModelScope.launch {
            try {
                val localPath = scanRepository.generateDishImage(itemId, token)
                imageStates.update { it + (itemId to DishImageState.Ready(localPath)) }
                revealedHistoryStore.updateImagePath(item, localPath)
            } catch (exc: Exception) {
                imageStates.update {
                    it + (
                        itemId to DishImageState.Failed(
                            exc.message ?: "Reference image could not be generated."
                        )
                    )
                }
            }
        }
    }

    private fun saveRevealedDish(itemId: String) {
        val item = scannedItems.value.firstOrNull { it.itemId == itemId } ?: return
        val imagePath = when (val state = imageStates.value[itemId]) {
            is DishImageState.Ready -> state.localPath
            else -> scanRepository.cachedDishImagePath(itemId)
        }
        viewModelScope.launch {
            revealedHistoryStore.saveRevealedDish(item, imagePath)
        }
    }
}
