package com.menulens.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.menulens.app.model.MenuItem
import com.menulens.app.ui.theme.Hairline
import com.menulens.app.ui.theme.Gold
import com.menulens.app.viewmodel.DishImageState
import com.menulens.app.viewmodel.ResultsUiState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    state: ResultsUiState,
    itemId: String,
    onBack: () -> Unit,
    onReveal: () -> Unit,
    onShowToStaff: () -> Unit,
    onRetryImage: () -> Unit
) {
    val item = state.itemById(itemId)
    val unlocked = state.isUnlocked(itemId)

    AppScreen(
        title = "",
        subtitle = "",
        showHeaderCard = false,
        topPadding = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Dish guide",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                )
            )
            Spacer(Modifier.size(48.dp))
        }
        EditorialDivider()
        if (item == null) {
            Text("Dish details are unavailable.", style = MaterialTheme.typography.bodyLarge)
            return@AppScreen
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DishIdentity(item)
                if (unlocked) {
                    GeneratedImageHero(
                        item = item,
                        imageState = state.imageState(itemId),
                        onRetry = onRetryImage
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        EditorialDivider()
                        Text(
                            text = item.preview.enTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.preview.enDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("dish_explanation")
                        )
                    }
                    if (item.preview.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            item.preview.tags.forEach { tag ->
                                Text(
                                    text = tag.replace('_', ' '),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            RoundedCornerShape(50)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Best-effort guidance; confirm with staff for allergies or dietary needs.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LockedDetailCard(
                        creditsRemaining = state.creditsRemainingToday,
                        isPro = state.isPro,
                        onReveal = onReveal
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = onShowToStaff,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 12.dp)
                    .height(50.dp)
                    .testTag("show_to_staff"),
                shape = RoundedCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(Icons.Outlined.PersonOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Show to staff · これをください", modifier = Modifier.padding(start = 7.dp))
            }
        }
    }
}

@Composable
private fun DishIdentity(item: MenuItem) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = item.jpText,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("detail_japanese_name")
        )
        item.priceText?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GeneratedImageHero(
    item: MenuItem,
    imageState: DishImageState,
    onRetry: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hairline, RoundedCornerShape(8.dp))
    ) {
        when (imageState) {
            is DishImageState.Ready -> {
                AsyncImage(
                    model = File(imageState.localPath),
                    contentDescription = "AI-generated reference for ${item.jpText}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .testTag("generated_image_ready")
                )
                Text(
                    text = "AI-generated reference · Actual presentation may vary.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).testTag("ai_image_label")
                )
            }
            DishImageState.Loading -> ImageSkeleton()
            is DishImageState.Failed -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "The reference image could not be prepared.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your dish explanation is still available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.testTag("retry_generated_image")
                    ) {
                        Text("Try image again")
                    }
                }
            }
            DishImageState.NotRequested -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (item.imageGenerationToken == null) {
                            "AI reference image is unavailable for this dish."
                        } else {
                            "Preparing image request…"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageSkeleton() {
    val alpha = rememberInfiniteTransition(label = "image-loading")
        .animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
            label = "image-loading-alpha"
        )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .alpha(alpha.value)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .testTag("generated_image_loading"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Creating a visual reference…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun LockedDetailCard(
    creditsRemaining: Int,
    isPro: Boolean,
    onReveal: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Hairline, RoundedCornerShape(9.dp))
            .testTag("locked_detail")
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "English name, explanation, tags, and visual reference are locked.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onReveal,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(7.dp)
            ) {
                Text(
                    when {
                        isPro -> "Reveal dish"
                        creditsRemaining > 0 -> "Reveal · $creditsRemaining left today"
                        else -> "Upgrade to unlock"
                    }
                )
            }
        }
    }
}
