package com.itsdonebro.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsdonebro.domain.MessageEngine
import com.itsdonebro.domain.TrackingState

/**
 * Full-screen blocking overlay shown when the daily Reel limit is hit.
 *
 * Features:
 *  - Pulsing 🚨 icon
 *  - Sarcastic message (from MessageEngine)
 *  - Reels watched + time stats
 *  - "I'M DONE BRO" and "CLOSE INSTAGRAM" buttons
 *  - Escalating messages for repeat attempts
 */
@Composable
fun BlockingOverlayContent(
    state: TrackingState,
    messageEngine: MessageEngine,
    onDone: () -> Unit,
    onCloseInstagram: () -> Unit,
    onBlockedAttempt: () -> Unit
) {
    // Pulsing animation for the warning icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val message = remember(state.blockAttempts) {
        if (state.blockAttempts > 0)
            messageEngine.getRepeatAttemptMessage(state.blockAttempts)
        else
            messageEngine.getLimitReachedMessage(
                state.reelsToday,
                state.watchTimeTodaySeconds / 60
            )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D0D),
                        Color(0xFF1A0A0A),
                        Color(0xFF0D0D0D)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Warning Icon ──────────────────────────────────────────────────
            Text(
                text = "🚨",
                fontSize = (64 * pulseScale).sp,
                textAlign = TextAlign.Center
            )

            // ── Headline ──────────────────────────────────────────────────────
            Text(
                text = "THAT'S ENOUGH",
                color = Color(0xFFFF3B30),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            // ── Stats Card ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatRow(label = "REELS WATCHED", value = "${state.reelsToday}")
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    StatRow(
                        label = "TIME DONATED",
                        value = formatSeconds(state.watchTimeTodaySeconds)
                    )
                }
            }

            // ── Sarcastic message ─────────────────────────────────────────────
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(8.dp))

            // ── Primary button ────────────────────────────────────────────────
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF3B30)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "I'M DONE BRO",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // ── Secondary button ──────────────────────────────────────────────
            OutlinedButton(
                onClick = onCloseInstagram,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White.copy(alpha = 0.7f)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(Color.White.copy(0.2f), Color.White.copy(0.2f)))
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "CLOSE INSTAGRAM",
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            letterSpacing = 2.sp
        )
    }
}
