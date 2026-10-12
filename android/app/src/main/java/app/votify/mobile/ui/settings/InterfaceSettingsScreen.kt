package app.votify.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.AppTheme
import app.votify.mobile.ui.theme.AmberPalette
import app.votify.mobile.ui.theme.AzurePalette
import app.votify.mobile.ui.theme.ColorPair
import app.votify.mobile.ui.theme.EmeraldPalette
import app.votify.mobile.ui.theme.OledBlackPalette
import app.votify.mobile.ui.theme.RosePalette
import app.votify.mobile.ui.theme.VioletPalette
import app.votify.mobile.ui.theme.VotifyColors

/** «Интерфейс»: режим темы, тема, вкладки, акцент под обложку, прозрачность, текст. */
@Composable
fun InterfaceSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val spec by viewModel.colorSpec.collectAsStateWithLifecycle()
    var colorSlot by remember { mutableStateOf<String?>(null) }
    // Какая пара сейчас активна: у пар фон — их собственный цвет, поэтому сравниваем
    // и акцент, и фон со спеком. Так подсветится выбранная плитка и не подсветятся другие.
    val currentPairKey by remember {
        derivedStateOf {
            ColorPair.entries.firstOrNull {
                it.backgroundHex.equals(spec.background, ignoreCase = true) &&
                    it.accentHex.equals(spec.primary, ignoreCase = true)
            }?.key
        }
    }

    SettingsScaffold(stringResource(R.string.settings_interface), contentPadding, onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            SettingsSectionLabel(stringResource(R.string.settings_theme))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_theme_mode),
                    subtitle = null,
                    value = themeModeLabel(prefs.themeMode),
                    trailing = Icons.Outlined.DarkMode,
                    options = listOf(
                        "dark" to stringResource(R.string.settings_theme_mode_dark),
                        "light" to stringResource(R.string.settings_theme_mode_light),
                        "system" to stringResource(R.string.settings_theme_mode_system),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(themeMode = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_theme_name),
                    subtitle = null,
                    value = themeNameLabel(prefs.themeName),
                    trailing = Icons.Outlined.Palette,
                    options = listOf(
                        "neutral" to stringResource(R.string.settings_theme_neutral),
                        "graphite" to stringResource(R.string.settings_theme_graphite),
                        "violet" to stringResource(R.string.settings_theme_violet),
                        "azure" to stringResource(R.string.settings_theme_azure),
                        "emerald" to stringResource(R.string.settings_theme_emerald),
                        "amber" to stringResource(R.string.settings_theme_amber),
                        "rose" to stringResource(R.string.settings_theme_rose),
                        "workshop" to stringResource(R.string.settings_theme_workshop),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(themeName = v) } },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_navigation))
            SettingsCard {
                SettingsToggleRow(
                    title = "Стиль «Остров»",
                    subtitle = "Плавающая закруглённая панель с отступами",
                    checked = prefs.navBarIsland,
                    onChange = { v -> viewModel.updatePrefs { it.copy(navBarIsland = v) } },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Размер панели (высота)",
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

            // Цвета интерфейса: готовые ЦВЕТОВЫЕ ПАРЫ (фон + акцент), затем прежние одноцветные темы.
            SettingsSectionLabel(stringResource(R.string.backgrounds_colors))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Цветовые пары",
                    style = MaterialTheme.typography.labelLarge,
                    color = VotifyColors.TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                // Сетка 2×5: каждая плитка — сама пара (фон и акцент рядом), чтобы было
                // сразу видно результат, а не один цвет.
                ColorPair.entries.chunked(2).forEach { rowItems ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { pair ->
                            ColorPairTile(
                                pair = pair,
                                selected = prefs.themeName == "workshop" && currentPairKey == pair.key,
                                onClick = {
                                    // Помечаем тему как «мастерскую», иначе следующий апдейт
                                    // настроек откатил бы цвет обратно к одноцветной теме.
                                    // themeApplyBackground=false — чтобы фон пары не перекрывался
                                    // собственной картинкой пользователя.
                                    viewModel.updatePrefs {
                                        it.copy(
                                            themeMode = "dark",
                                            themeName = "workshop",
                                            themeApplyBackground = false,
                                        )
                                    }
                                    viewModel.setCustomColors(pair.toSlotMap())
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // При нечётном числе пар в последнем ряду добиваем пустым местом.
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PaletteSwatch(OledBlackPalette.primary, stringResource(R.string.swatch_black_white)) { viewModel.applyPalettePreset(AppTheme.OledBlack) }
                PaletteSwatch(RosePalette.primary, stringResource(R.string.settings_theme_rose)) { viewModel.applyPalettePreset(AppTheme.Rose) }
                PaletteSwatch(VioletPalette.primary, stringResource(R.string.settings_theme_violet)) { viewModel.applyPalettePreset(AppTheme.Violet) }
                PaletteSwatch(AzurePalette.primary, stringResource(R.string.settings_theme_azure)) { viewModel.applyPalettePreset(AppTheme.Azure) }
                PaletteSwatch(EmeraldPalette.primary, stringResource(R.string.settings_theme_emerald)) { viewModel.applyPalettePreset(AppTheme.Emerald) }
                PaletteSwatch(AmberPalette.primary, stringResource(R.string.settings_theme_amber)) { viewModel.applyPalettePreset(AppTheme.Amber) }
            }
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                Column(Modifier.padding(12.dp)) {
                    ColorSlotRow(stringResource(R.string.color_primary), spec.primary) { colorSlot = "primary" }
                    ColorSlotRow(stringResource(R.string.color_background), spec.background) { colorSlot = "background" }
                    ColorSlotRow(stringResource(R.string.color_text), spec.text) { colorSlot = "text" }
                    ColorSlotRow(stringResource(R.string.color_cards), spec.cards) { colorSlot = "cards" }
                    ColorSlotRow(stringResource(R.string.color_borders), spec.borders) { colorSlot = "borders" }
                }
            }

            SettingsSectionLabel(stringResource(R.string.settings_transparency))
            SettingsCard {
                SettingsToggleRow(
                    title = "Liquid Glass",
                    subtitle = "Одно стекло на весь интерфейс: карточки, панели и нижняя панель",
                    checked = prefs.glassEnabled,
                    onChange = { v -> viewModel.updatePrefs { it.copy(glassEnabled = v) } },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Прозрачность",
                    value = prefs.glassAlpha.toFloat(),
                    range = 0f..100f,
                    unit = "%",
                    onValueChange = { v -> viewModel.updatePrefs { it.copy(glassAlpha = v.toInt()) } },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Размытие",
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

    colorSlot?.let { key ->
        ColorPickerDialog(
            title = stringResource(
                when (key) {
                    "primary" -> R.string.color_primary
                    "background" -> R.string.color_background
                    "text" -> R.string.color_text
                    "cards" -> R.string.color_cards
                    else -> R.string.color_borders
                },
            ),
            initialHex = when (key) {
                "primary" -> spec.primary
                "background" -> spec.background
                "text" -> spec.text
                "cards" -> spec.cards
                else -> spec.borders
            },
            onApply = { hex ->
                viewModel.setCustomColors(mapOf(key to hex))
                colorSlot = null
            },
            onDismiss = { colorSlot = null },
        )
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
