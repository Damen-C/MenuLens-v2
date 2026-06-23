package com.menulens.app.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DishImageRequestPolicyTest {
    @Test
    fun lockedDishNeverRequestsImage() {
        assertFalse(
            DishImageRequestPolicy.shouldRequest(
                unlocked = false,
                state = DishImageState.NotRequested
            )
        )
    }

    @Test
    fun firstRevealRequestsExactlyOnce() {
        assertTrue(
            DishImageRequestPolicy.shouldRequest(
                unlocked = true,
                state = DishImageState.NotRequested
            )
        )
        assertFalse(
            DishImageRequestPolicy.shouldRequest(
                unlocked = true,
                state = DishImageState.Loading
            )
        )
    }

    @Test
    fun reopeningReadyImageUsesCacheWithoutRequest() {
        assertFalse(
            DishImageRequestPolicy.shouldRequest(
                unlocked = true,
                state = DishImageState.Ready("/cache/dish.webp")
            )
        )
    }

    @Test
    fun failureRequiresExplicitRetry() {
        val failed = DishImageState.Failed("network")
        assertFalse(DishImageRequestPolicy.shouldRequest(unlocked = true, state = failed))
        assertTrue(
            DishImageRequestPolicy.shouldRequest(
                unlocked = true,
                state = failed,
                forceRetry = true
            )
        )
    }
}
