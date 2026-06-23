package com.menulens.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EntitlementStoreTest {

    @Test
    fun resetCredits_whenTokyoDayChanged_setsDefaultCredits() {
        val today = "2026-02-14"
        val result = EntitlementStore.resetCreditsIfNeeded(
            storedDateTokyo = "2026-02-13",
            credits = 0,
            todayTokyo = today
        )

        assertEquals(today, result.first)
        assertEquals(EntitlementStore.DEFAULT_DAILY_CREDITS, result.second)
    }

    @Test
    fun resetCredits_sameTokyoDay_keepsExistingCredits() {
        val today = "2026-02-14"
        val result = EntitlementStore.resetCreditsIfNeeded(
            storedDateTokyo = today,
            credits = 2,
            todayTokyo = today
        )

        assertEquals(today, result.first)
        assertEquals(2, result.second)
    }
}
