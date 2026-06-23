package com.menulens.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun PaywallScreen(
    isPro: Boolean,
    creditsRemaining: Int,
    onEnablePro: () -> Unit,
    onDisablePro: () -> Unit,
    onRestore: () -> Unit
) {
    AppScreen(
        title = "Understand every dish",
        subtitle = "MenuLens Pro removes reveal limits while keeping ordering simple.",
        showBrandAsBlock = true,
        topPadding = 24.dp
    ) {
        EditorialDivider()
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            Text(
                text = if (isPro) "Pro is active" else "$creditsRemaining free reveals remaining today",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Benefit("Unlimited English dish explanations")
            Benefit("One AI-generated visual reference per revealed dish")
            Benefit("Japanese ordering card always available")
            EditorialDivider()
            Button(
                onClick = if (isPro) onDisablePro else onEnablePro,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(49.dp)
                    .testTag("debug_pro_toggle"),
                shape = RoundedCornerShape(7.dp)
            ) {
                Text(if (isPro) "Disable Pro · Debug" else "Enable Pro · Debug")
            }
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth().height(47.dp),
                shape = RoundedCornerShape(7.dp)
            ) {
                Text("Restore purchases")
            }
            Text(
                text = "Debug subscription controls are temporary and do not process payment.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Benefit(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("—", color = MaterialTheme.colorScheme.primary)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
