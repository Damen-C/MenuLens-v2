package com.menulens.app.ui.screens

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.menulens.app.R
import com.menulens.app.ui.theme.Hairline
import com.menulens.app.viewmodel.ResultsUiState
import com.menulens.app.viewmodel.ScanPhase
import kotlinx.coroutines.delay

private val scanStages = listOf(
    "Reading Japanese text",
    "Separating dishes and prices",
    "Writing concise English guidance"
)
private const val STAGE_DURATION_MS = 1150L
private const val MINIMUM_PROCESSING_PRESENTATION_MS = STAGE_DURATION_MS * 3 + 250L

@Composable
fun ProcessingScreen(
    state: ResultsUiState,
    onStartProcessing: () -> Unit,
    onRetry: () -> Unit,
    onBackToScan: () -> Unit,
    onProcessingComplete: () -> Unit
) {
    var activeStage by remember { mutableIntStateOf(0) }
    val processingStartedAt = remember { SystemClock.elapsedRealtime() }

    LaunchedEffect(Unit) {
        onStartProcessing()
        activeStage = 0
        while (true) {
            delay(STAGE_DURATION_MS)
            activeStage = (activeStage + 1) % scanStages.size
        }
    }
    LaunchedEffect(state.scanPhase) {
        if (state.scanPhase == ScanPhase.SUCCESS) {
            val elapsed = SystemClock.elapsedRealtime() - processingStartedAt
            delay((MINIMUM_PROCESSING_PRESENTATION_MS - elapsed).coerceAtLeast(0L))
            onProcessingComplete()
        }
    }

    AppScreen(
        title = if (state.scanPhase == ScanPhase.ERROR) "We couldn’t read that menu" else "Reading your menu",
        subtitle = if (state.scanPhase == ScanPhase.ERROR) {
            "Try the same image again, or return for a clearer photo."
        } else {
            "This scan prepares dish text only. Visual references are created later, after a reveal."
        },
        centerContent = false,
        showBrandAsBlock = true,
        topPadding = 28.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (state.scanPhase == ScanPhase.ERROR) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    EditorialDivider()
                    Text(
                        text = state.scanErrorMessage ?: "The scan did not complete.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp).testTag("scan_error")
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(7.dp)
                    ) {
                        Text("Try again")
                    }
                    OutlinedButton(
                        onClick = onBackToScan,
                        modifier = Modifier.fillMaxWidth().height(47.dp),
                        shape = RoundedCornerShape(7.dp)
                    ) {
                        Text("Choose another photo")
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.processing_editorial),
                        contentDescription = "Japanese menu text being scanned and translated",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Hairline, RoundedCornerShape(8.dp))
                    )
                    ProcessingSequence(
                        activeStage = activeStage,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Column {
                EditorialDivider()
                Text(
                    "Your uploaded menu image is processed for this request and is not retained by MenuLens.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp, bottom = 22.dp)
                )
            }
        }
    }
}

@Composable
private fun ProcessingSequence(
    activeStage: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing-sequence")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active-stage-pulse"
    )
    val sweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = STAGE_DURATION_MS.toInt()),
            repeatMode = RepeatMode.Restart
        ),
        label = "connector-sweep"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ANALYZING MENU",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(28.dp))
        scanStages.forEachIndexed { index, label ->
            AnimatedStageRow(
                number = index + 1,
                label = label,
                active = index == activeStage,
                pulse = pulse
            )
            if (index < scanStages.lastIndex) {
                AnimatedConnector(
                    emphasized = index == activeStage,
                    sweep = sweep
                )
            }
        }
    }
}

@Composable
private fun AnimatedStageRow(
    number: Int,
    label: String,
    active: Boolean,
    pulse: Float
) {
    val markerColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(320),
        label = "stage-marker-color"
    )
    val numberColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(320),
        label = "stage-number-color"
    )
    val labelColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(320),
        label = "stage-label-color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .scale(if (active) pulse else 1f)
                .background(markerColor, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = numberColor
            )
        }
        Spacer(Modifier.width(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = labelColor
            )
            Text(
                text = if (active) "In progress" else "Waiting",
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
            )
        }
    }
}

@Composable
private fun AnimatedConnector(
    emphasized: Boolean,
    sweep: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(start = 18.dp)
                .width(2.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.outline)
        ) {
            if (emphasized) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((28 * sweep).dp.coerceAtLeast(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
