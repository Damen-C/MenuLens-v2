package com.menulens.app.viewmodel

enum class RevealDecision {
    OPEN_ALREADY_UNLOCKED,
    OPEN_WITH_PRO,
    CONSUME_CREDIT_AND_OPEN,
    OPEN_PAYWALL
}

object RevealPolicy {
    fun decide(isUnlocked: Boolean, isPro: Boolean, creditsRemainingToday: Int): RevealDecision {
        return when {
            isUnlocked -> RevealDecision.OPEN_ALREADY_UNLOCKED
            isPro -> RevealDecision.OPEN_WITH_PRO
            creditsRemainingToday > 0 -> RevealDecision.CONSUME_CREDIT_AND_OPEN
            else -> RevealDecision.OPEN_PAYWALL
        }
    }
}
