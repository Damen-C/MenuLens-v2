package com.menulens.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.menulens.app.ui.theme.Hairline
import com.menulens.app.ui.theme.Canvas
import com.menulens.app.ui.theme.Gold
import com.menulens.app.ui.theme.Paper
import com.menulens.app.ui.theme.Vermilion

@Composable
fun ShowToStaffScreen(
    jpText: String,
    priceText: String?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .padding(horizontal = 34.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .border(1.dp, Hairline, RoundedCornerShape(5.dp))
                .testTag("show_to_staff_japanese_only"),
            shape = RoundedCornerShape(5.dp),
            colors = CardDefaults.cardColors(containerColor = Paper),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                OrderSlipCorners()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "これをください",
                        fontSize = 30.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = Vermilion,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("staff_request_phrase")
                    )
                    Spacer(Modifier.height(26.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = 54.dp)
                            .background(Gold.copy(alpha = 0.55f))
                    )
                    Spacer(Modifier.height(30.dp))
                    Text(
                        text = jpText,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 46.sp,
                            lineHeight = 56.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("staff_japanese_dish")
                    )
                    Spacer(Modifier.height(28.dp))
                    Box(
                        modifier = Modifier.height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        priceText?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 30.sp,
                                    lineHeight = 38.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("staff_price")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderSlipCorners() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        val length = 12.dp.toPx()
        val inset = 2.dp.toPx()
        val color = Gold.copy(alpha = 0.38f)
        val stroke = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)

        fun corner(origin: Offset, horizontalDirection: Float, verticalDirection: Float) {
            drawLine(
                color = color,
                start = Offset(origin.x + horizontalDirection * inset, origin.y),
                end = Offset(origin.x + horizontalDirection * length, origin.y),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(origin.x, origin.y + verticalDirection * inset),
                end = Offset(origin.x, origin.y + verticalDirection * length),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
            drawArc(
                color = color,
                startAngle = when {
                    horizontalDirection > 0 && verticalDirection > 0 -> 180f
                    horizontalDirection < 0 && verticalDirection > 0 -> 270f
                    horizontalDirection > 0 && verticalDirection < 0 -> 90f
                    else -> 0f
                },
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(
                    origin.x - if (horizontalDirection < 0) length else 0f,
                    origin.y - if (verticalDirection < 0) length else 0f
                ),
                size = androidx.compose.ui.geometry.Size(length, length),
                style = stroke
            )
        }

        corner(Offset(0f, 0f), 1f, 1f)
        corner(Offset(size.width, 0f), -1f, 1f)
        corner(Offset(0f, size.height), 1f, -1f)
        corner(Offset(size.width, size.height), -1f, -1f)
    }
}
