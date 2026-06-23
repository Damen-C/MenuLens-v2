package com.menulens.app

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.menulens.app.auth.FirebaseAuthManager
import com.menulens.app.navigation.AppNavGraph
import com.menulens.app.ui.theme.MenuLensTheme

@Composable
fun MenuLensApp() {
    LaunchedEffect(Unit) {
        runCatching { FirebaseAuthManager.ensureSignedInAnonymously() }
            .onFailure { exc -> Log.e("MenuLensApp", "Firebase anonymous sign-in failed", exc) }
    }

    MenuLensTheme {
        AppNavGraph()
    }
}
