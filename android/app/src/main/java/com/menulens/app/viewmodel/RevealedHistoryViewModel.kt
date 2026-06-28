package com.menulens.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.menulens.app.data.RevealedDishHistoryEntry
import com.menulens.app.data.RevealedHistoryStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RevealedHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RevealedHistoryStore(application.applicationContext)

    val entries: StateFlow<List<RevealedDishHistoryEntry>> = store.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun entryById(id: String): RevealedDishHistoryEntry? = entries.value.firstOrNull { it.id == id }

    fun clearHistory() {
        viewModelScope.launch {
            store.clear()
        }
    }
}
