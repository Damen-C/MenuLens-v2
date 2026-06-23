package com.menulens.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun ResultsScreen(
    state: ResultsUiState,
    onItemTap: (String) -> Unit
) {
    AppScreen(
        title = "Your menu",
        subtitle = "Japanese names and prices stay visible.",
        showBrandAsBlock = true,
        topPadding = 16.dp
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.items.size} dishes found",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (!state.isPro) {
                        Text(
                            text = "${state.creditsRemainingToday} reveals left",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(state.items, key = { it.itemId }) { item ->
                DishResultCard(
                    item = item,
                    unlocked = state.isUnlocked(item.itemId),
                    imageState = state.imageState(item.itemId),
                    onClick = { onItemTap(item.itemId) }
                )
            }
        }
    }
}

@Composable
private fun DishResultCard(
    item: MenuItem,
    unlocked: Boolean,
    imageState: DishImageState,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (unlocked) "dish_unlocked_${item.itemId}" else "dish_locked_${item.itemId}")
            .border(1.dp, Hairline, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 15.dp, top = 13.dp, end = 11.dp, bottom = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.jpText,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                item.priceText?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (unlocked) {
                    Text(
                        text = item.preview.enTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Tap to reveal details",
                        style = MaterialTheme.typography.labelMedium,
                        color = Gold,
                        modifier = Modifier.testTag("locked_copy_${item.itemId}")
                    )
                }
            }
            if (unlocked && imageState is DishImageState.Ready) {
                AsyncImage(
                    model = File(imageState.localPath),
                    contentDescription = "AI-generated reference for ${item.jpText}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(62.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .testTag("dish_thumbnail_${item.itemId}")
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (unlocked) Icons.Outlined.RestaurantMenu else Icons.Outlined.Lock,
                        contentDescription = if (unlocked) "Open dish" else "Locked",
                        tint = if (unlocked) MaterialTheme.colorScheme.secondary else Gold,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
