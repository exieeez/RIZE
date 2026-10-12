package app.votify.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.votify.mobile.data.CustomPrefs
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild

/**
 * ОБЩЕЕ СТЕКЛО ДЛЯ ВСЕГО ПРИЛОЖЕНИЯ.
 *
 * Раньше нижняя панель и интерфейс настраивались раздельно: панель брала
 * navBarAlpha/navBarBlur и умела настоящее размытие (haze), а карточки брали
 * cardsAlpha/cardsBlur и рисовали просто полупрозрачный градиент без блюра —
 * поэтому в интерфейсе был виден «только цвет», а не матовое стекло.
 *
 * Теперь есть одна формула ([GlassSpec]) и один источник размытия ([LocalHazeState]),
 * общие для панели и для всех поверхностей интерфейса.
 */

/** Общее состояние размытия: источник — слой обоев, потребители — стеклянные поверхности. */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * ЕДИНАЯ формула стекла — одна и та же для нижней панели и для интерфейса.
 *
 * @param enabled включён ли режим Liquid Glass
 * @param alphaFraction прозрачность 0..1 (1 — самое прозрачное стекло)
 * @param blurDp запрошенная сила размытия 0..60 dp
 */
data class GlassSpec(
    val enabled: Boolean,
    val alphaFraction: Float,
    val blurDp: Int,
) {
    /** Тон матового стекла поверх размытого фона. */
    val tintAlpha: Float get() = ((1f - alphaFraction) * 0.45f).coerceIn(0.10f, 0.50f)

    /**
     * Прозрачное стекло без блюра вырождается в плоскую заливку цветом — именно так
     * это и выглядело. Поэтому при включённом стекле минимальные 20 dp остаются всегда.
     */
    val effectiveBlurDp: Int
        get() = when {
            !enabled -> 0
            blurDp > 0 -> blurDp
            alphaFraction > 0f -> 20
            else -> 0
        }

    /** Тонкая светлая кромка стекла. */
    val borderAlpha: Float get() = (0.05f + alphaFraction * 0.07f).coerceIn(0.04f, 0.12f)
}

/** Собирает [GlassSpec] из переданных настроек (без чтения CompositionLocal). */
fun CustomPrefs.toGlassSpec(): GlassSpec = GlassSpec(
    enabled = glassEnabled,
    alphaFraction = glassAlpha.coerceIn(0, 100) / 100f,
    blurDp = glassBlur.coerceIn(0, 60),
)

/**
 * Собирает [GlassSpec] из текущих настроек интерфейса.
 *
 * Читает [LocalCustomPrefs], поэтому вызывать только ВНУТРИ провайдера настроек.
 * Там, где настройки известны напрямую (например при формировании самой палитры),
 * используйте [toGlassSpec], иначе возьмутся значения по умолчанию.
 */
@Composable
fun rememberGlassSpec(): GlassSpec = LocalCustomPrefs.current.toGlassSpec()

/**
 * Настоящее матовое стекло: размывает то, что лежит ПОД поверхностью, ровно как
 * нижняя панель.
 *
 * Важно про haze 0.7.x: размывается содержимое узла-источника, а узел с эффектом
 * не должен быть его потомком. Поэтому источник (слой обоев) лежит рядом, а не
 * внутри содержимого. Если блюр недоступен (стекло выключено, нет источника или
 * нулевой радиус) — рисуем плотную заливку, чтобы поверхность не «пропала».
 */
@Composable
fun Modifier.votifyGlass(
    shape: Shape,
    baseColor: Color,
    spec: GlassSpec = rememberGlassSpec(),
    hazeState: HazeState? = LocalHazeState.current,
): Modifier {
    val blurDp = spec.effectiveBlurDp
    return if (spec.enabled && hazeState != null && blurDp > 0) {
        this.hazeChild(
            state = hazeState,
            shape = shape,
            style = HazeStyle(
                tint = baseColor.copy(alpha = spec.tintAlpha),
                blurRadius = blurDp.dp,
            ),
        )
    } else {
        this
            .clip(shape)
            .background(baseColor.copy(alpha = if (spec.enabled) spec.alphaFraction else 1f))
    }
}
