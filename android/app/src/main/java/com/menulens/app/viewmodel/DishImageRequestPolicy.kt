package com.menulens.app.viewmodel

object DishImageRequestPolicy {
    fun shouldRequest(
        unlocked: Boolean,
        state: DishImageState,
        forceRetry: Boolean = false
    ): Boolean {
        if (!unlocked) return false
        return if (forceRetry) {
            state is DishImageState.Failed
        } else {
            state is DishImageState.NotRequested
        }
    }
}
