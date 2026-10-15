package app.votify.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.ui.theme.VotifyColors

/** «Интерфейс»: режим темы, тема, вкладки, акцент под обложку, прозрачность, текст. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    var showThemeModal by remember { mutableStateOf(false) }

    if (showThemeModal) {
        ThemeModeBottomSheet(
            currentTheme = prefs.themeName,
            accentFromArt = prefs.accentFromArt,
            onThemeSelect = { name ->
                viewModel.updatePrefs { it.copy(themeName = name, themeMode = "dark") }
            },
            onAccentFromArtToggle = { enabled ->
                viewModel.updatePrefs { it.copy(accentFromArt = enabled) }
            },
            onDismiss = { showThemeModal = false },
        )
    }

    SettingsScaffold(stringResource(R.string.settings_interface), contentPadding, onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            SettingsSectionLabel(stringResource(R.string.settings_theme))
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeModal = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Тема",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = themeDisplayName(prefs.themeName),
                            style = MaterialTheme.typography.bodySmall,
                            color = VotifyColors.TextSecondary,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(themeColor(prefs.themeName))
                            .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(11.dp)),
                    )

                    Spacer(Modifier.width(10.dp))

                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = VotifyColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            SettingsSectionLabel(stringResource(R.string.settings_navigation))
            SettingsCard {
                SettingsToggleRow(
                    title = stringResource(R.string.island_style),
                    subtitle = stringResource(R.string.island_sub),
                    checked = prefs.navBarIsland,
                    onChange = { v -> viewModel.updatePrefs { it.copy(navBarIsland = v) } },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = stringResource(R.string.dock_height),
                    value = prefs.navBarHeight.toFloat(),
                    range = 48f..80f,
                    unit = " dp",
                    onValueChange = { v -> viewModel.updatePrefs { it.copy(navBarHeight = v.toInt()) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_accent))
            SettingsCard {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_accent_art),
                    subtitle = stringResource(R.string.settings_accent_art_sub),
                    checked = prefs.accentFromArt,
                    onChange = { v -> viewModel.updatePrefs { it.copy(accentFromArt = v) } },
                )
            }

            // Блок «Цвета интерфейса» убран по просьбе: он был источником кринжовой
            // смены акцента и ломал вид интерфейса. Тема берётся из списка «Тема» выше,
            // а акцент под обложку остаётся тумблером выше.

            SettingsSectionLabel(stringResource(R.string.settings_transparency))
            SettingsCard {
                SettingsToggleRow(
                    title = "Liquid Glass",
                    subtitle = stringResource(R.string.glass_sub),
                    checked = prefs.glassEnabled,
                    onChange = { v -> viewModel.updatePrefs { it.copy(glassEnabled = v) } },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = stringResource(R.string.settings_transparency),
                    value = prefs.glassAlpha.toFloat(),
                    range = 0f..100f,
                    unit = "%",
                    onValueChange = { v -> viewModel.updatePrefs { it.copy(glassAlpha = v.toInt()) } },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = stringResource(R.string.settings_effect_blur),
                    value = prefs.glassBlur.toFloat(),
                    range = 0f..60f,
                    unit = " dp",
                    onValueChange = { v -> viewModel.updatePrefs { it.copy(glassBlur = v.toInt()) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_text))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_font),
                    subtitle = null,
                    value = fontFamilyLabel(prefs.fontFamily),
                    trailing = Icons.Outlined.TextFields,
                    options = listOf(
                        "system" to stringResource(R.string.settings_font_system),
                        "serif" to stringResource(R.string.settings_font_serif),
                        "mono" to stringResource(R.string.settings_font_mono),
                        "rounded" to stringResource(R.string.settings_font_rounded),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(fontFamily = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_font_size),
                    subtitle = null,
                    value = fontScaleLabel(prefs.fontScale),
                    trailing = Icons.Outlined.FormatSize,
                    options = listOf(
                        "small" to stringResource(R.string.settings_font_small),
                        "normal" to stringResource(R.string.settings_font_normal),
                        "large" to stringResource(R.string.settings_font_large),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(fontScale = v) } },
                )
            }
        }
    }
}

@Composable
private fun themeModeLabel(v: String): String = stringResource(
    when (v) {
        "light" -> R.string.settings_theme_mode_light
        "system" -> R.string.settings_theme_mode_system
        else -> R.string.settings_theme_mode_dark
    },
)

@Composable
private fun themeNameLabel(v: String): String = stringResource(
    when (v) {
        "graphite" -> R.string.settings_theme_graphite
        "violet" -> R.string.settings_theme_violet
        "azure" -> R.string.settings_theme_azure
        "emerald" -> R.string.settings_theme_emerald
        "amber" -> R.string.settings_theme_amber
        "rose" -> R.string.settings_theme_rose
        "workshop" -> R.string.settings_theme_workshop
        else -> R.string.settings_theme_neutral
    },
)

@Composable
private fun fontFamilyLabel(v: String): String = stringResource(
    when (v) {
        "serif" -> R.string.settings_font_serif
        "mono" -> R.string.settings_font_mono
        "rounded" -> R.string.settings_font_rounded
        else -> R.string.settings_font_system
    },
)

@Composable
private fun fontScaleLabel(v: String): String = stringResource(
    when (v) {
        "small" -> R.string.settings_font_small
        "large" -> R.string.settings_font_large
        else -> R.string.settings_font_normal
    },
)

private data class ThemeOption(
    val key: String,
    val title: String,
    val color: Color,
)

private val THEME_OPTIONS = listOf(
    ThemeOption("neutral", "Neutral", Color(0xFFE5E7EB)),
    ThemeOption("amoled", "AMOLED", Color(0xFF000000)),
    ThemeOption("sky", "Sky", Color(0xFF3B82F6)),
    ThemeOption("mint", "Mint", Color(0xFF10B981)),
    ThemeOption("violet", "Violet", Color(0xFF8B5CF6)),
    ThemeOption("blossom", "Blossom", Color(0xFFF43F5E)),
    ThemeOption("sand", "Sand", Color(0xFFF5A623)),
    ThemeOption("aqua", "Aqua", Color(0xFF06B6D4)),
)

private fun themeDisplayName(key: String): String = when (key.lowercase()) {
    "amoled", "graphite" -> "AMOLED"
    "sky", "azure" -> "Sky"
    "mint", "emerald" -> "Mint"
    "violet" -> "Violet"
    "blossom", "rose" -> "Blossom"
    "sand", "amber" -> "Sand"
    "aqua" -> "Aqua"
    "workshop" -> "Workshop"
    else -> "Neutral"
}

private fun themeColor(key: String): Color = when (key.lowercase()) {
    "amoled", "graphite" -> Color(0xFF000000)
    "sky", "azure" -> Color(0xFF3B82F6)
    "mint", "emerald" -> Color(0xFF10B981)
    "violet" -> Color(0xFF8B5CF6)
    "blossom", "rose" -> Color(0xFFF43F5E)
    "sand", "amber" -> Color(0xFFF5A623)
    "aqua" -> Color(0xFF06B6D4)
    "workshop" -> Color(0xFFF5A623)
    else -> Color(0xFFE5E7EB)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeBottomSheet(
    currentTheme: String,
    accentFromArt: Boolean,
    onThemeSelect: (String) -> Unit,
    onAccentFromArtToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141416),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 12.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Режим темы",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )

            Spacer(Modifier.height(16.dp))

            // Dynamic accent toggle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1C1C1F))
                    .clickable { onAccentFromArtToggle(!accentFromArt) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Под обложку трека",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Динамический цвет элементов плеера",
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.TextSecondary,
                    )
                }
                Switch(
                    checked = accentFromArt,
                    onCheckedChange = onAccentFromArtToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF3B82F6),
                        uncheckedThumbColor = Color(0xFF9E9E9E),
                        uncheckedTrackColor = Color(0xFF2C2C2E),
                    ),
                )
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(14.dp))

            Text(
                text = "Цветовые темы",
                style = MaterialTheme.typography.labelMedium,
                color = VotifyColors.TextMuted,
            )

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1C1C1F)),
            ) {
                THEME_OPTIONS.forEachIndexed { index, opt ->
                    val isSelected = currentTheme.equals(opt.key, ignoreCase = true) ||
                            (opt.key == "amoled" && currentTheme.equals("graphite", ignoreCase = true)) ||
                            (opt.key == "sky" && currentTheme.equals("azure", ignoreCase = true)) ||
                            (opt.key == "mint" && currentTheme.equals("emerald", ignoreCase = true)) ||
                            (opt.key == "blossom" && currentTheme.equals("rose", ignoreCase = true)) ||
                            (opt.key == "sand" && currentTheme.equals("amber", ignoreCase = true))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onThemeSelect(opt.key)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(opt.color)
                                .border(
                                    width = if (opt.key == "amoled") 1.dp else 0.5.dp,
                                    color = Color.White.copy(alpha = 0.25f),
                                    shape = CircleShape,
                                ),
                        )

                        Spacer(Modifier.width(14.dp))

                        Text(
                            text = opt.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )

                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    if (index < THEME_OPTIONS.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color.White.copy(alpha = 0.05f),
                        )
                    }
                }
            }
        }
    }
}
