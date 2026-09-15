package com.itsdonebro.overlay

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsdonebro.domain.TrackingState

/**
 * The compact floating bubble that appears above Instagram while tracking.
 *
 * Shows:
 *   🔴  <reels count>   ⏱  <time>
 *   "<contextual message>"
 */
@Composable
fun FloatingCounterContent(state: TrackingState) {
    val progress = state.limitProgress.coerceIn(0f, 1f)

    // Bubble color transitions from green → orange → red as limit approaches
    val bubbleColor = when {
        progress >= 0.9f -> Color(0xFFFF3B30)   // red — almost done
        progress >= 0.6f -> Color(0xFFFF9500)   // orange — getting there
        else             -> Color(0xFF1C1C1E)   // dark — normal
    }

    AnimatedVisibility(
        visible = state.isInstagramForeground,
        enter = fadeIn() + slideInVertically { -it },
        exit  = fadeOut() + slideOutVertically { -it }
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(bubbleColor.copy(alpha = 0.92f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main counter row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🔴 ${state.reelsToday}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "⏱ ${formatSeconds(state.watchTimeTodaySeconds)}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Contextual message (only show if there's content)
                if (state.currentMessage.isNotBlank() && state.reelsToday > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.currentMessage.lines().firstOrNull() ?: "",
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

fun formatSeconds(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
