package com.menulens.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class RevealPolicyTest {

    @Test
    fun reveal_whenNotProAndHasCredits_consumesCreditPath() {
        val decision = RevealPolicy.decide(
            isUnlocked = false,
            isPro = false,
            creditsRemainingToday = 2
        )

        assertEquals(RevealDecision.CONSUME_CREDIT_AND_OPEN, decision)
    }

    @Test
    fun reveal_whenNoCreditsAndNotPro_opensPaywall() {
        val decision = RevealPolicy.decide(
            isUnlocked = false,
            isPro = false,
            creditsRemainingToday = 0
        )

        assertEquals(RevealDecision.OPEN_PAYWALL, decision)
    }

    @Test
    fun reveal_whenAlreadyUnlocked_doesNotConsumeAgain() {
        val decision = RevealPolicy.decide(
            isUnlocked = true,
            isPro = false,
            creditsRemainingToday = 0
        )

        assertEquals(RevealDecision.OPEN_ALREADY_UNLOCKED, decision)
    }
}
