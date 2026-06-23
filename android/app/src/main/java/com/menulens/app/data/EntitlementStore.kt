package com.menulens.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

private val Context.entitlementDataStore by preferencesDataStore(name = "entitlement_prefs")

private object EntitlementKeys {
    val creditsRemaining = intPreferencesKey("credits_remaining_today")
    val lastResetDateTokyo = stringPreferencesKey("last_reset_date_tokyo")
    val isPro = booleanPreferencesKey("is_pro_debug")
}

data class EntitlementState(
    val creditsRemainingToday: Int,
    val isPro: Boolean
)

class EntitlementStore(private val context: Context) {

    val state: Flow<EntitlementState> = context.entitlementDataStore.data.map { prefs ->
        EntitlementState(
            creditsRemainingToday = prefs[EntitlementKeys.creditsRemaining] ?: DEFAULT_DAILY_CREDITS,
            isPro = prefs[EntitlementKeys.isPro] ?: false
        )
    }

    suspend fun refreshCreditsIfNeeded() {
        val todayTokyo = todayInTokyo()
        context.entitlementDataStore.edit { prefs ->
            val last = prefs[EntitlementKeys.lastResetDateTokyo]
            if (last != todayTokyo) {
                prefs[EntitlementKeys.lastResetDateTokyo] = todayTokyo
                prefs[EntitlementKeys.creditsRemaining] = DEFAULT_DAILY_CREDITS
            } else if (prefs[EntitlementKeys.creditsRemaining] == null) {
                prefs[EntitlementKeys.creditsRemaining] = DEFAULT_DAILY_CREDITS
            }
        }
    }

    suspend fun tryConsumeCredit(): Boolean {
        var consumed = false
        context.entitlementDataStore.edit { prefs ->
            val remaining = prefs[EntitlementKeys.creditsRemaining] ?: DEFAULT_DAILY_CREDITS
            if (remaining > 0) {
                prefs[EntitlementKeys.creditsRemaining] = remaining - 1
                consumed = true
            }
        }
        return consumed
    }

    suspend fun setProEnabled(enabled: Boolean) {
        context.entitlementDataStore.edit { prefs ->
            prefs[EntitlementKeys.isPro] = enabled
        }
    }

    suspend fun resetProForRestoreStub() {
        context.entitlementDataStore.edit { prefs ->
            prefs[EntitlementKeys.isPro] = false
        }
    }

    companion object {
        const val DEFAULT_DAILY_CREDITS = 3

        fun todayInTokyo(): String = LocalDate.now(ZoneId.of("Asia/Tokyo")).toString()

        fun resetCreditsIfNeeded(
            storedDateTokyo: String?,
            credits: Int,
            todayTokyo: String
        ): Pair<String, Int> {
            return if (storedDateTokyo != todayTokyo) {
                todayTokyo to DEFAULT_DAILY_CREDITS
            } else {
                todayTokyo to credits
            }
        }
    }
}

