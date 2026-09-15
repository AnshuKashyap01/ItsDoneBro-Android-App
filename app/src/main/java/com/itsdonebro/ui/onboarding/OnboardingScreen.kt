package com.itsdonebro.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsdonebro.ui.theme.*

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val actionLabel: String? = null,
    val actionIntent: Intent? = null
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current

    val pages = remember {
        listOf(
            OnboardingPage(
                emoji = "👋",
                title = "Hey.",
                subtitle = "I'm ItsDoneBro.\n\nI don't hate Reels.\n\nI just think you and your thumb\nneed a little supervision."
            ),
            OnboardingPage(
                emoji = "📊",
                title = "I'll count everything.",
                subtitle = "Reels watched.\nTime spent.\nDaily limits.\n\nAll of it."
            ),
            OnboardingPage(
                emoji = "⏱",
                title = "Set a limit.",
                subtitle = "How much Reel time do you want per day?\n\nBe honest.\nBe brave.\nPick a number."
            ),
            OnboardingPage(
                emoji = "🚫",
                title = "Then I block them.",
                subtitle = "When you hit your limit, I cover the Reel\nwith a blocking screen.\n\nYou're welcome."
            ),
            OnboardingPage(
                emoji = "♿",
                title = "One permission needed.",
                subtitle = "I need Accessibility access to detect\nwhen you're in the Reel dimension.\n\nDon't worry.\nI'm not here to read your DMs.",
                actionLabel = "Enable Accessibility",
                actionIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            ),
            OnboardingPage(
                emoji = "🪟",
                title = "And one more.",
                subtitle = "Overlay permission lets me show the\nfloating counter and blocking screen\nabove Instagram.",
                actionLabel = "Enable Overlay",
                actionIntent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                )
            )
        )
    }

    var currentPage by remember { mutableIntStateOf(0) }
    val page = pages[currentPage]
    val isLast = currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Background, Color(0xFF100808)))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Skip button ────────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!isLast) {
                    TextButton(onClick = onFinished) {
                        Text("Skip", color = OnSurfaceDim, fontSize = 14.sp)
                    }
                }
            }

            // ── Main content ───────────────────────────────────────────────────
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInHorizontally { it / 4 })
                        .togetherWith(fadeOut(tween(200)) + slideOutHorizontally { -it / 4 })
                },
                label = "page"
            ) { pageIdx ->
                val pg = pages[pageIdx]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Emoji in a glowing circle
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(BrandRed.copy(0.25f), Color.Transparent)
                                )
                            )
                            .border(1.dp, BrandRed.copy(0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(pg.emoji, fontSize = 52.sp)
                    }

                    Text(
                        pg.title,
                        color = OnBackground,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        pg.subtitle,
                        color = OnSurfaceDim,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center
                    )

                    // Permission action button (only on permission pages)
                    if (pg.actionLabel != null && pg.actionIntent != null) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    pg.actionIntent.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandRed),
                            border = BorderStroke(1.dp, BrandRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(pg.actionLabel, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Bottom: dots + next button ─────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Page dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages.forEachIndexed { idx, _ ->
                        Box(
                            Modifier
                                .size(if (idx == currentPage) 24.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (idx == currentPage) BrandRed else OnSurfaceFaint
                                )
                                .animateContentSize()
                        )
                    }
                }

                // Next / Get started
                Button(
                    onClick = {
                        if (isLast) onFinished()
                        else currentPage++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        if (isLast) "LET'S GO 🔥" else "NEXT",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
