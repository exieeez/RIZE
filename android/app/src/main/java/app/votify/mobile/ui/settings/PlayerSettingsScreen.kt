package app.votify.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.ArtworkStyle
import app.votify.mobile.data.PlayerBackground

/** «Плеер»: стиль, заголовок, слайдер, плашка, фон + секция мини-плеера (100% дизайн со скриншотов). */
@Composable
fun PlayerSettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenPresets: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_player), contentPadding, onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {

            // СТИЛЬ ПЛЕЕРА
            SettingsSectionLabel(stringResource(R.string.settings_player_style))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_player_style),
                    subtitle = null,
                    value = when (settings.artworkStyle) {
                        ArtworkStyle.Square -> stringResource(R.string.player_style_standard)
                        ArtworkStyle.Blur -> stringResource(R.string.player_style_big)
                        else -> stringResource(R.string.player_style_record)
                    },
                    trailing = when (settings.artworkStyle) {
                        ArtworkStyle.Square -> Icons.Outlined.MusicNote
                        ArtworkStyle.Blur -> Icons.Outlined.Tv
                        else -> Icons.Outlined.Album
                    },
                    choiceOptions = listOf(
                        ChoiceOption("square", stringResource(R.string.player_style_standard), stringResource(R.string.player_style_standard_desc), Icons.Outlined.MusicNote),
                        ChoiceOption("blur", stringResource(R.string.player_style_big), stringResource(R.string.player_style_big_desc), Icons.Outlined.Tv),
                        ChoiceOption("vinyl", stringResource(R.string.player_style_record), stringResource(R.string.player_style_record_desc), Icons.Outlined.Album),
                    ),
                    onPick = { key ->
                        when (key) {
                            "square" -> viewModel.setArtworkStyle(ArtworkStyle.Square)
                            "blur" -> viewModel.setArtworkStyle(ArtworkStyle.Blur)
                            else -> viewModel.setArtworkStyle(ArtworkStyle.Vinyl)
                        }
                        viewModel.updatePrefs { it.copy(playerStyle = key) }
                    },
                )
            }

            // ЗАГОЛОВОК
            SettingsSectionLabel(stringResource(R.string.settings_title_section))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_title_align),
                    subtitle = null,
                    value = if (prefs.titleAlign == "left") stringResource(R.string.settings_align_left) else stringResource(R.string.settings_align_center),
                    trailing = if (prefs.titleAlign == "left") Icons.Outlined.FormatAlignLeft else Icons.Outlined.FormatAlignCenter,
                    choiceOptions = listOf(
                        ChoiceOption("center", stringResource(R.string.settings_align_center), null, Icons.Outlined.FormatAlignCenter),
                        ChoiceOption("left", stringResource(R.string.settings_align_left), null, Icons.Outlined.FormatAlignLeft),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(titleAlign = v) } },
                )
            }

            // СЛАЙДЕР
            SettingsSectionLabel(stringResource(R.string.settings_slider))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_slider_type),
                    subtitle = when (prefs.sliderStyle) {
                        "thin" -> stringResource(R.string.slider_thin)
                        "classic" -> stringResource(R.string.player_style_standard)
                        "wave" -> stringResource(R.string.slider_wave)
                        else -> "iOS"
                    },
                    value = when (prefs.sliderStyle) {
                        "thin" -> stringResource(R.string.slider_thin)
                        "classic" -> stringResource(R.string.player_style_standard)
                        "wave" -> stringResource(R.string.slider_wave)
                        else -> "iOS"
                    },
                    trailing = when (prefs.sliderStyle) {
                        "thin" -> Icons.Outlined.Remove
                        "classic" -> Icons.Outlined.RadioButtonUnchecked
                        "wave" -> Icons.Outlined.Equalizer
                        else -> Icons.Outlined.ToggleOn
                    },
                    choiceOptions = listOf(
                        ChoiceOption("classic", stringResource(R.string.player_style_standard), null, Icons.Outlined.RadioButtonUnchecked),
                        ChoiceOption("thin", stringResource(R.string.slider_thin), null, Icons.Outlined.Remove),
                        ChoiceOption("ios", "iOS", null, Icons.Outlined.ToggleOn),
                        ChoiceOption("wave", stringResource(R.string.slider_wave), null, Icons.Outlined.Equalizer),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(sliderStyle = v) } },
                )
            }

            // ПЛАШКА ИНФОРМАЦИИ
            SettingsSectionLabel(stringResource(R.string.settings_info_chip))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_info_chip_show),
                    subtitle = when (prefs.infoChip) {
                        "text" -> stringResource(R.string.player_lyrics)
                        "none" -> stringResource(R.string.opt_none)
                        else -> stringResource(R.string.settings_chip_source)
                    },
                    value = when (prefs.infoChip) {
                        "text" -> stringResource(R.string.player_lyrics)
                        "none" -> stringResource(R.string.opt_none)
                        else -> stringResource(R.string.settings_chip_source)
                    },
                    trailing = when (prefs.infoChip) {
                        "text" -> Icons.Outlined.Description
                        "none" -> Icons.Outlined.Block
                        else -> Icons.Outlined.Info
                    },
                    choiceOptions = listOf(
                        ChoiceOption("source", stringResource(R.string.settings_chip_source), null, Icons.Outlined.Info),
                        ChoiceOption("text", stringResource(R.string.player_lyrics), null, Icons.Outlined.Description),
                        ChoiceOption("none", stringResource(R.string.opt_none), null, Icons.Outlined.Block),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(infoChip = v) } },
                )
            }

            // ФОН ПЛЕЕРА
            SettingsSectionLabel(stringResource(R.string.settings_player_bg))
            SettingsCard {
                SettingsValueRow(
                    title = stringResource(R.string.settings_player_bg),
                    subtitle = when (settings.playerBackground) {
                        PlayerBackground.Artwork -> stringResource(R.string.settings_artwork)
                        PlayerBackground.Lava -> stringResource(R.string.opt_color)
                        else -> stringResource(R.string.opt_none)
                    },
                    value = when (settings.playerBackground) {
                        PlayerBackground.Artwork -> stringResource(R.string.settings_artwork)
                        PlayerBackground.Lava -> stringResource(R.string.opt_color)
                        else -> stringResource(R.string.opt_none)
                    },
                    trailing = when (settings.playerBackground) {
                        PlayerBackground.Artwork -> Icons.Outlined.Image
                        PlayerBackground.Lava -> Icons.Outlined.Palette
                        else -> Icons.Outlined.Block
                    },
                    choiceOptions = listOf(
                        ChoiceOption("artwork", stringResource(R.string.settings_artwork), stringResource(R.string.bg_artwork_desc), Icons.Outlined.Image),
                        ChoiceOption("lava", stringResource(R.string.opt_color), stringResource(R.string.bg_lava_desc), Icons.Outlined.Palette),
                        ChoiceOption("dark", stringResource(R.string.opt_none), stringResource(R.string.bg_dark_desc), Icons.Outlined.Block),
                    ),
                    onPick = { v ->
                        viewModel.setPlayerBackground(
                            when (v) {
                                "artwork" -> PlayerBackground.Artwork
                                "lava" -> PlayerBackground.Lava
                                else -> PlayerBackground.Dark
                            },
                        )
                    },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.settings_lyrics_over_art),
                    subtitle = stringResource(R.string.settings_lyrics_over_art_sub),
                    checked = settings.showLyricsOverArtwork,
                    onChange = viewModel::setShowLyricsOverArtwork,
                )
            }

            // МИНИ-ПЛЕЕР
            SettingsSectionLabel(stringResource(R.string.settings_mini_player))
            SettingsCard {
                SettingsNavRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = stringResource(R.string.settings_mini_presets),
                    subtitle = stringResource(R.string.settings_mini_presets_sub),
                    onClick = onOpenPresets,
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_mini_bg),
                    subtitle = if (prefs.miniBg == "artwork") stringResource(R.string.settings_mini_bg_artwork) else stringResource(R.string.settings_mini_bg_plain),
                    value = if (prefs.miniBg == "artwork") stringResource(R.string.settings_mini_bg_artwork) else stringResource(R.string.settings_mini_bg_plain),
                    trailing = Icons.Outlined.Palette,
                    choiceOptions = listOf(
                        ChoiceOption("artwork", stringResource(R.string.settings_mini_bg_artwork), null, Icons.Outlined.Palette),
                        ChoiceOption("plain", stringResource(R.string.settings_mini_bg_plain), null, Icons.Outlined.Block),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniBg = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_mini_progress),
                    subtitle = when (prefs.miniProgress) {
                        "bar" -> stringResource(R.string.settings_mini_progress_bar)
                        "none" -> stringResource(R.string.opt_none)
                        else -> stringResource(R.string.settings_mini_progress_ring)
                    },
                    value = when (prefs.miniProgress) {
                        "bar" -> stringResource(R.string.settings_mini_progress_bar)
                        "none" -> stringResource(R.string.opt_none)
                        else -> stringResource(R.string.settings_mini_progress_ring)
                    },
                    trailing = Icons.Outlined.Equalizer,
                    choiceOptions = listOf(
                        ChoiceOption("ring", stringResource(R.string.settings_mini_progress_ring), null, Icons.Outlined.RadioButtonUnchecked),
                        ChoiceOption("bar", stringResource(R.string.settings_mini_progress_bar), null, Icons.Outlined.Remove),
                        ChoiceOption("none", stringResource(R.string.opt_none), null, Icons.Outlined.Block),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniProgress = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_mini_cover),
                    subtitle = if (prefs.miniCoverShape == "circle") stringResource(R.string.settings_shape_circle) else stringResource(R.string.opt_rounded),
                    value = if (prefs.miniCoverShape == "circle") stringResource(R.string.settings_shape_circle) else stringResource(R.string.opt_rounded),
                    trailing = Icons.Outlined.RadioButtonUnchecked,
                    choiceOptions = listOf(
                        ChoiceOption("circle", stringResource(R.string.settings_shape_circle), null, Icons.Outlined.RadioButtonUnchecked),
                        ChoiceOption("rounded", stringResource(R.string.opt_rounded), null, Icons.Outlined.CropSquare),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniCoverShape = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_mini_corners),
                    subtitle = when (prefs.miniCorners) {
                        "none" -> stringResource(R.string.opt_none)
                        "soft" -> stringResource(R.string.corners_soft)
                        "rounded" -> stringResource(R.string.opt_rounded)
                        else -> stringResource(R.string.settings_shape_pill)
                    },
                    value = when (prefs.miniCorners) {
                        "none" -> stringResource(R.string.opt_none)
                        "soft" -> stringResource(R.string.corners_soft)
                        "rounded" -> stringResource(R.string.opt_rounded)
                        else -> stringResource(R.string.settings_shape_pill)
                    },
                    trailing = Icons.Outlined.RadioButtonUnchecked,
                    choiceOptions = listOf(
                        ChoiceOption("none", stringResource(R.string.opt_none), null, Icons.Outlined.CropSquare),
                        ChoiceOption("soft", stringResource(R.string.corners_soft), null, Icons.Outlined.GridView),
                        ChoiceOption("rounded", stringResource(R.string.opt_rounded), null, Icons.Outlined.GridView),
                        ChoiceOption("pill", stringResource(R.string.settings_shape_pill), null, Icons.Outlined.RadioButtonUnchecked),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniCorners = v) } },
                )
                SettingsDivider()
                SettingsValueRow(
                    title = stringResource(R.string.settings_mini_button_style),
                    subtitle = when (prefs.miniButtonStyle) {
                        "standard" -> stringResource(R.string.mini_btn_standard)
                        "minimal" -> stringResource(R.string.mini_btn_minimal)
                        else -> stringResource(R.string.settings_button_filled)
                    },
                    value = when (prefs.miniButtonStyle) {
                        "standard" -> stringResource(R.string.mini_btn_standard)
                        "minimal" -> stringResource(R.string.mini_btn_minimal)
                        else -> stringResource(R.string.settings_button_filled)
                    },
                    trailing = Icons.Outlined.RadioButtonUnchecked,
                    choiceOptions = listOf(
                        ChoiceOption("standard", stringResource(R.string.mini_btn_standard), null, Icons.Outlined.MoreHoriz),
                        ChoiceOption("filled", stringResource(R.string.settings_button_filled), null, Icons.Outlined.RadioButtonUnchecked),
                        ChoiceOption("minimal", stringResource(R.string.mini_btn_minimal), null, Icons.Outlined.Grain),
                    ),
                    onPick = { v -> viewModel.updatePrefs { it.copy(miniButtonStyle = v) } },
                )
            }
        }
    }
}
