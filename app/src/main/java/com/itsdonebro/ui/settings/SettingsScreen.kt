package com.itsdonebro.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itsdonebro.data.preferences.BlockingMode
import com.itsdonebro.data.preferences.PersonalityMode
import com.itsdonebro.ui.theme.*

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Background,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Settings", color = OnBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = OnSurfaceDim)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Tracking ───────────────────────────────────────────────────────
            SettingsSection(title = "TRACKING") {
                SettingsToggleRow(
                    label = "Track Instagram Reels",
                    subtitle = "Enable or pause all tracking",
                    checked = state.trackingEnabled,
                    onCheckedChange = viewModel::setTrackingEnabled
                )
            }

            // ── Daily Limit ────────────────────────────────────────────────────
            SettingsSection(title = "DAILY LIMIT") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Daily limit", color = OnSurface, fontSize = 15.sp)
                        Text("${state.dailyLimitMinutes} min", color = BrandRed, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = state.dailyLimitMinutes.toFloat(),
                        onValueChange = { viewModel.setDailyLimit(it.toInt()) },
                        valueRange = 5f..180f,
                        steps = 34,
                        colors = SliderDefaults.colors(
                            thumbColor = BrandRed,
                            activeTrackColor = BrandRed,
                            inactiveTrackColor = SurfaceVariant
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("5 min", color = OnSurfaceDim, fontSize = 11.sp)
                        Text("3 hours", color = OnSurfaceDim, fontSize = 11.sp)
                    }
                }
            }

            // ── Blocking mode ──────────────────────────────────────────────────
            SettingsSection(title = "BLOCKING") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mode after limit is reached", color = OnSurfaceDim, fontSize = 13.sp)
                    BlockingMode.values().forEach { mode ->
                        BlockingModeRow(
                            mode = mode,
                            selected = state.blockingMode == mode,
                            onSelect = { viewModel.setBlockingMode(mode) }
                        )
                    }
                    if (state.blockingMode == BlockingMode.COOLDOWN) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cooldown duration", color = OnSurface, fontSize = 15.sp)
                            Text("${state.cooldownMinutes} min", color = BrandOrange, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = state.cooldownMinutes.toFloat(),
                            onValueChange = { viewModel.setCooldown(it.toInt()) },
                            valueRange = 5f..120f,
                            steps = 22,
                            colors = SliderDefaults.colors(
                                thumbColor = BrandOrange,
                                activeTrackColor = BrandOrange,
                                inactiveTrackColor = SurfaceVariant
                            )
                        )
                    }
                }
            }

            // ── Floating Counter ───────────────────────────────────────────────
            SettingsSection(title = "FLOATING COUNTER") {
                SettingsToggleRow("Show overlay", checked = state.overlayEnabled, onCheckedChange = viewModel::setOverlayEnabled)
                if (state.overlayEnabled) {
                    Divider(color = Divider)
                    SettingsToggleRow("Show Reel count", checked = state.showCounter, onCheckedChange = viewModel::setShowCounter)
                    Divider(color = Divider)
                    SettingsToggleRow("Show timer", checked = state.showTimer, onCheckedChange = viewModel::setShowTimer)
                }
            }

            // ── Personality ────────────────────────────────────────────────────
            SettingsSection(title = "PERSONALITY") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PersonalityMode.values().forEach { mode ->
                        PersonalityRow(
                            mode = mode,
                            selected = state.personalityMode == mode,
                            onSelect = { viewModel.setPersonality(mode) }
                        )
                    }
                }
            }

            // ── Notifications ──────────────────────────────────────────────────
            SettingsSection(title = "NOTIFICATIONS") {
                SettingsToggleRow("Limit warning", checked = state.notifWarning, onCheckedChange = viewModel::setNotifWarning)
                Divider(color = Divider)
                SettingsToggleRow("Daily summary", checked = state.notifSummary, onCheckedChange = viewModel::setNotifSummary)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Reusable setting components ──────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            title,
            color = OnSurfaceDim,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = OnSurface, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = OnSurfaceDim, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandRed
            )
        )
    }
}

@Composable
private fun BlockingModeRow(mode: BlockingMode, selected: Boolean, onSelect: () -> Unit) {
    val (label, desc) = when (mode) {
        BlockingMode.HARD_BLOCK    -> "Hard Block" to "Reels blocked for the rest of the day"
        BlockingMode.WARNING_ONLY  -> "Warning Only" to "Warn you, but let you continue"
        BlockingMode.COOLDOWN      -> "Cooldown" to "Block temporarily, then resume"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BrandRed.copy(0.15f) else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = if (selected) BrandRed else OnSurface, fontWeight = FontWeight.Medium)
            Text(desc, color = OnSurfaceDim, fontSize = 12.sp)
        }
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = BrandRed)
        )
    }
}

@Composable
private fun PersonalityRow(mode: PersonalityMode, selected: Boolean, onSelect: () -> Unit) {
    val (label, emoji) = when (mode) {
        PersonalityMode.FRIENDLY_SARCASTIC -> "Friendly Sarcastic" to "😏"
        PersonalityMode.MOTIVATIONAL       -> "Motivational" to "💪"
        PersonalityMode.MINIMAL            -> "Minimal" to "🤫"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) BrandRed.copy(0.15f) else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$emoji  $label", color = if (selected) BrandRed else OnSurface, fontWeight = FontWeight.Medium)
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = BrandRed)
        )
    }
}
