package app.votify.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import app.votify.mobile.R
import app.votify.mobile.data.Track
import app.votify.mobile.ui.theme.VotifyColors
import coil.compose.SubcomposeAsyncImage

fun resolveCoverModel(url: String?): Any? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("data:image/")) {
        val commaIdx = url.indexOf(',')
        if (commaIdx != -1) {
            val base64Data = url.substring(commaIdx + 1)
            return runCatching {
                android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            }.getOrNull()
        }
    }
    if (url.startsWith("file://")) {
        return java.io.File(url.removePrefix("file://"))
    }
    if (url.startsWith("/")) {
        return java.io.File(url)
    }
    return url
}

/** Square artwork with a graceful placeholder. */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    contentDescription: String? = null,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
) {
    val model = androidx.compose.runtime.remember(url) { resolveCoverModel(url) }
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(VotifyColors.SurfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Icon(Icons.Default.MusicNote, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(size * 0.45f))
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Icon(Icons.Default.MusicNote, null, tint = VotifyColors.TextMuted, modifier = Modifier.size(size * 0.45f))
                },
            )
        }
    }
}

/**
 * Track row from the design: 56dp, 44dp thumbnail, title (#E5E5EA) + artist (#8E8E93),
 * trailing overflow button. Highlights the currently playing track.
 */
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean = false,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    downloaded: Boolean = false,
    downloadFraction: Float? = null,
) {
    Column(modifier.fillMaxWidth()) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(track.cover, size = 44.dp)
            if (downloaded && downloadFraction == null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DownloadDone,
                        contentDescription = stringResource(R.string.track_badge_downloaded),
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
            if (isCurrent) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) VotifyColors.TextPrimary else VotifyColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(track.artist)
                    if (track.duration > 0) append(" • ").append(formatDuration(track.duration * 1000L))
                    if (downloadFraction != null) append(" • ").append((downloadFraction * 100).toInt()).append('%')
                },
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Outlined.MoreVert, null, tint = VotifyColors.TextMuted)
            }
        }
    }
    if (downloadFraction != null) {
        // Thin hand-rolled progress bar (no deprecated progress APIs).
        Box(
            Modifier
                .padding(horizontal = 12.dp, vertical = 3.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(VotifyColors.SurfaceContainerHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(downloadFraction.coerceIn(0.02f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VotifyColors.Primary),
            )
        }
    }
}
}

/** Pill filter chip: white fill when selected, #1E1E1E + hairline border otherwise. */
@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) VotifyColors.Primary else VotifyColors.SurfaceContainer,
        contentColor = if (selected) VotifyColors.OnPrimary else VotifyColors.TextMuted,
        border = if (selected) null else BorderStroke(1.dp, VotifyColors.BorderSubtle),
        modifier = modifier.height(36.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                letterSpacing = 0.sp,
            )
        }
    }
}

/** Global CustomPrefs for reactive UI styling (Settings -> Interface). */
/**
 * ВНИМАНИЕ: настоящее размытие haze работает только на Android 12L+ (API 32+);
 * ниже система рисует полупрозрачную заливку вместо блюра.
 */
val LocalCustomPrefs = androidx.compose.runtime.compositionLocalOf { app.votify.mobile.data.CustomPrefs() }

/** Global toggle for Liquid Glass mode (Settings -> Interface). */
val LocalLiquidGlass = androidx.compose.runtime.compositionLocalOf { false }

/**
 * ВНИМАНИЕ: настоящее размытие haze работает только на Android 12L+ (API 32+);
 * ниже система рисует полупрозрачную заливку вместо блюра (см. ScrimImpl в haze).
 * На таких устройствах ползунок «Размытие» визуально ничего не меняет — это
 * ограничение платформы, а не настроек.
 */

/** Level-2 card: solid surface by default; frosted blur and alpha when enabled in settings matching the nav bar 1-to-1. */
@Composable
fun VotifyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    color: Color = VotifyColors.SurfaceContainer,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // Единое стекло: та же формула, что у нижней панели — карточки и панель совпадают.
    val spec = rememberGlassSpec()

    val baseModifier = if (spec.enabled) {
        modifier
            .votifyGlass(shape = shape, baseColor = color, spec = spec)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = spec.borderAlpha),
                shape = shape,
            )
    } else {
        modifier
            .clip(shape)
            .background(color)
            .border(0.5.dp, VotifyColors.BorderSubtle.copy(alpha = 0.4f), shape)
    }
    Box(
        (if (onClick != null) baseModifier.clickable {
            VotifyHaptics.click(view, context)
            onClick()
        } else baseModifier).padding(contentPadding),
    ) { content() }
}

/** Circular 40dp icon action button on a solid disc (or liquid glass if enabled). */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    filled: Boolean = false,
    contentDescription: String? = null,
    /** Переопределить цвет диска (например приглушённым акцентом вместо чистого). */
    fillColor: Color? = null,
    /** Переопределить цвет содержимого диска. */
    tintColor: Color? = null,
    /**
     * Рисовать ли матовое стекло. Выключается там, где кнопка лежит НА стекле
     * (например в мини-плеере) — иначе размытие накладывалось бы само на себя.
     */
    useGlass: Boolean = true,
    /** Рисовать ли светлую кромку вокруг диска. */
    showBorder: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val spec = rememberGlassSpec()
    val hazeState = LocalHazeState.current

    // Раньше при включённом стекле кнопки получали просто белёсую плёнку без размытия —
    // рядом с матовыми карточками это выглядело как другой материал. Теперь тот же
    // материал, что у нижней панели.
    val glassModifier = if (!filled && useGlass) {
        Modifier.votifyGlass(
            shape = CircleShape,
            baseColor = if (spec.enabled) GlassBaseColor else VotifyColors.SurfaceContainer,
            spec = spec,
            hazeState = hazeState,
        )
    } else Modifier

    val backgroundColor = fillColor ?: if (filled) VotifyColors.Primary else Color.Transparent
    val contentColor = tintColor ?: if (filled) VotifyColors.OnPrimary else VotifyColors.TextPrimary
    val borderModifier = if (filled || !showBorder || !spec.enabled) {
        Modifier
    } else {
        Modifier.border(0.5.dp, Color.White.copy(alpha = spec.borderAlpha), CircleShape)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(glassModifier)
            .background(backgroundColor, CircleShape)
            .then(borderModifier)
            .clickable(
                onClick = {
                    VotifyHaptics.click(view, context)
                    onClick()
                },
                role = androidx.compose.ui.semantics.Role.Button,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor
        ) {
            icon()
        }
    }
}

/** Section header row: "СЛУШАЙТЕ ПРЯМО СЕЙЧАС ......... Все". */
@Composable
fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = VotifyColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.bodySmall,
                color = VotifyColors.TextMuted,
                modifier = Modifier.clickable(enabled = onAction != null) { onAction?.invoke() },
            )
        }
    }
}

/** "3 трека" / "5 треков" — Russian plural forms from res/values/strings.xml. */
@Composable
fun pluralTracks(count: Int): String = pluralStringResource(R.plurals.tracks_count, count, count)

/**
 * OutlinedTextField in Votify colors (same treatment as the playlist-name dialog):
 * white text, #48484A hairline that lights up to white when focused.
 */
@Composable
fun VotifyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    /** Скрывать вводимый текст (флаг для секретных полей вроде пароля). */
    masked: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        label = { Text(label) },
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = VotifyColors.TextPrimary,
            unfocusedTextColor = VotifyColors.TextPrimary,
            cursorColor = VotifyColors.TextPrimary,
            focusedBorderColor = VotifyColors.TextPrimary,
            unfocusedBorderColor = VotifyColors.BorderProminent,
            focusedLabelColor = VotifyColors.TextSecondary,
            unfocusedLabelColor = VotifyColors.TextMuted,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
