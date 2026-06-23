package com.menulens.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class AppViewModelTest {

    @Test
    fun uiState_startsWithScanRoute() {
        val viewModel = AppViewModel()

        assertEquals("scan", viewModel.uiState.value.currentRoute)
    }
}
