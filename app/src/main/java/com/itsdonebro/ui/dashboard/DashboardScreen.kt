package com.itsdonebro.ui.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itsdonebro.data.db.DailyStats
import com.itsdonebro.domain.TrackingState
import com.itsdonebro.overlay.formatSeconds
import com.itsdonebro.ui.theme.*

@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isAccessibilityEnabled = remember {
        Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        ) == 1
    }
    val isOverlayEnabled = remember {
        Settings.canDrawOverlays(context)
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            DashboardTopBar(onSettingsClick = onNavigateToSettings)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Permission warnings
            if (!isAccessibilityEnabled) {
                PermissionBanner(
                    message = "My Reel radar is offline. Enable Accessibility to track.",
                    buttonText = "FIX IT",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }
                )
            }
            if (!isOverlayEnabled) {
                PermissionBanner(
                    message = "I can't see me. Enable Overlay permission to show the counter.",
                    buttonText = "FIX IT",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }
                )
            }

            // Hero stats
            HeroStatsCard(state = uiState.trackingState)

            // Progress bar
            LimitProgressCard(state = uiState.trackingState)

            // Personality message
            if (uiState.trackingState.reelsToday > 0 || uiState.trackingState.isLimitReached) {
                MessageCard(message = uiState.trackingState.currentMessage)
            }

            // Weekly summary
            if (uiState.weeklyStats.isNotEmpty()) {
                WeeklySummaryCard(stats = uiState.weeklyStats)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ─── Top bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(onSettingsClick: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    "ItsDoneBro",
                    color = OnBackground,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
                Text(
                    "Because \"one more Reel\" is a lie.",
                    color = OnSurfaceDim,
                    fontSize = 11.sp
                )
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = OnSurfaceDim
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
    )
}

// ─── Hero stats card ──────────────────────────────────────────────────────────

@Composable
private fun HeroStatsCard(state: TrackingState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A0A0A), Color(0xFF1C1C22))
                )
            )
            .padding(28.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (state.reelsToday == 0) "Today's Activity 😌" else "Today's Damage 😭",
                color = OnSurfaceDim,
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HeroStat(
                    value = "${state.reelsToday}",
                    label = "REELS\nWATCHED"
                )
                // Vertical divider
                Box(
                    Modifier
                        .width(1.dp)
                        .height(72.dp)
                        .background(Divider)
                )
                HeroStat(
                    value = formatSeconds(state.watchTimeTodaySeconds),
                    label = "TIME\nDONATED"
                )
            }

            // Tracking indicator
            Spacer(Modifier.height(16.dp))
            if (state.isInstagramForeground && state.isTracking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(BrandGreen, shape = RoundedCornerShape(50))
                    )
                    Text("Tracking active", color = BrandGreen, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = OnBackground,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            color = OnSurfaceDim,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
    }
}

// ─── Progress bar card ────────────────────────────────────────────────────────

@Composable
private fun LimitProgressCard(state: TrackingState) {
    val progress = state.limitProgress.coerceIn(0f, 1f)
    val limitMinutes = state.dailyLimitSeconds / 60

    val progressColor = when {
        progress >= 1.0f  -> BrandRed
        progress >= 0.85f -> BrandOrange
        else              -> BrandGreen
    }

    // Animate progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DAILY LIMIT",
                color = OnSurfaceDim,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp
            )
            Text(
                "${limitMinutes}m",
                color = progressColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50)),
            color = progressColor,
            trackColor = SurfaceVariant,
            strokeCap = StrokeCap.Round
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${(progress * 100).toInt()}% used",
                color = if (state.isLimitReached) BrandRed else OnSurfaceDim,
                fontSize = 12.sp
            )
            if (state.isLimitReached) {
                Text("🚫 LIMIT REACHED", color = BrandRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                val remaining = state.dailyLimitSeconds - state.watchTimeTodaySeconds
                Text(
                    "${remaining / 60}m remaining",
                    color = OnSurfaceDim,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ─── Message card ──────────────────────────────────────────────────────────────

@Composable
private fun MessageCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "\"${message.lines().first()}\"",
            color = OnSurface.copy(alpha = 0.8f),
            fontSize = 14.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─── Weekly summary card ───────────────────────────────────────────────────────

@Composable
private fun WeeklySummaryCard(stats: List<DailyStats>) {
    val totalReels = stats.sumOf { it.reelsWatched }
    val totalSeconds = stats.sumOf { it.totalWatchTimeSeconds }
    val hours = totalSeconds / 3600
    val mins  = (totalSeconds % 3600) / 60

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceCard)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "THIS WEEK",
            color = OnSurfaceDim,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$totalReels", color = OnBackground, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("Reels", color = OnSurfaceDim, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                    color = OnBackground, fontSize = 28.sp, fontWeight = FontWeight.Black
                )
                Text("Total time", color = OnSurfaceDim, fontSize = 12.sp)
            }
        }
        if (totalReels > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "That's ${if (hours > 0) "${hours}h ${mins}m" else "${mins} minutes"} of your life that could've been: coding, touching grass, literally anything 😭",
                color = OnSurfaceDim,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// ─── Permission banner ────────────────────────────────────────────────────────

@Composable
private fun PermissionBanner(message: String, buttonText: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2A1A1A))
            .border(1.dp, BrandRed.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
        Text(message, color = OnSurface.copy(0.8f), fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) {
            Text(buttonText, color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
