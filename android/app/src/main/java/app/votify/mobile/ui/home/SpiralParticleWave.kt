package app.votify.mobile.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.votify.mobile.ui.theme.VotifyColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Particle3D(
    val baseRadius: Float,
    val theta: Float,
    val ribbonOffset: Float,
    val zBase: Float,
    val size: Float,
    val scatterDirX: Float,
    val scatterDirY: Float,
    val scatterDirZ: Float,
    val alphaFactor: Float,
)

@Composable
fun SpiralParticleWave(
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    accentColor: Color = VotifyColors.Primary,
    isPlaying: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    // Generate ~1500 particles for the intertwined 3D ribbons
    val particleCount = 1400
    val particles = remember {
        val list = ArrayList<Particle3D>(particleCount)
        val random = Random(42)
        val arms = 3
        for (i in 0 until particleCount) {
            val arm = i % arms
            val t = (i.toFloat() / particleCount) // 0..1 along spiral
            val theta = t * (4.5f * 2f * PI.toFloat()) + (arm * (2f * PI.toFloat() / arms))
            // Spiral radius expanding from center
            val baseRadius = 15f + t * 115f
            // Width jitter across the spiral ribbon
            val ribbonOffset = (random.nextFloat() - 0.5f) * (18f + t * 24f)
            val zBase = sin(theta * 2f) * (15f + t * 30f) + (random.nextFloat() - 0.5f) * 16f

            // Explosion vector for scatter animation
            val u = random.nextFloat() * 2f * PI.toFloat()
            val v = (random.nextFloat() - 0.5f) * PI.toFloat()
            val dist = 140f + random.nextFloat() * 160f
            val sX = cos(v) * cos(u) * dist
            val sY = sin(v) * dist
            val sZ = cos(v) * sin(u) * dist

            list.add(
                Particle3D(
                    baseRadius = baseRadius,
                    theta = theta,
                    ribbonOffset = ribbonOffset,
                    zBase = zBase,
                    size = 1.2f + random.nextFloat() * 1.8f,
                    scatterDirX = sX,
                    scatterDirY = sY,
                    scatterDirZ = sZ,
                    alphaFactor = 0.4f + random.nextFloat() * 0.6f,
                )
            )
        }
        list
    }

    // Continuous 3D rotation transition
    val infiniteTransition = rememberInfiniteTransition(label = "wave3d")
    val rotationSpeed = if (isPlaying) 12_000 else 24_000
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    // Pulse animation when playing
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 800 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // Scatter / Gather transition
    val scatterAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isScattered by remember { mutableStateOf(false) }

    fun toggleScatter() {
        scope.launch {
            if (scatterAnim.isRunning) return@launch
            if (!isScattered) {
                isScattered = true
                scatterAnim.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
                // Automatically gather back after a brief pause
                kotlinx.coroutines.delay(400)
                scatterAnim.animateTo(0f, animationSpec = tween(900, easing = FastOutSlowInEasing))
                isScattered = false
            } else {
                scatterAnim.animateTo(0f, animationSpec = tween(800, easing = FastOutSlowInEasing))
                isScattered = false
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clickable(interactionSource = interactionSource, indication = null) {
                toggleScatter()
                onClick?.invoke()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseScale = (this.size.minDimension / 300f) * pulseScale
            val scatter = scatterAnim.value
            val rotRad = Math.toRadians(rotationAngle.toDouble()).toFloat()

            // Tilt on X axis for isometric 3D view
            val tiltAngle = Math.toRadians(32.0).toFloat()
            val cosTilt = cos(tiltAngle)
            val sinTilt = sin(tiltAngle)

            // Draw center glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.22f * (1f - scatter * 0.5f)),
                        accentColor.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = 90f * baseScale,
                ),
                radius = 90f * baseScale,
                center = center,
            )

            // Project 3D points to 2D
            for (p in particles) {
                // Current position on the spiral in model coordinates
                val currentTheta = p.theta + rotRad
                val r = p.baseRadius + p.ribbonOffset

                // 3D coordinates in spiral
                var x = r * cos(currentTheta)
                var y = r * sin(currentTheta)
                var z = p.zBase

                // Apply scatter offset if animating
                if (scatter > 0f) {
                    x += p.scatterDirX * scatter
                    y += p.scatterDirY * scatter
                    z += p.scatterDirZ * scatter
                }

                // Rotate around X axis (tilt view)
                val yTilted = y * cosTilt - z * sinTilt
                val zTilted = y * sinTilt + z * cosTilt

                // Perspective projection: scale by depth Z
                val cameraDistance = 380f
                val perspective = cameraDistance / (cameraDistance - zTilted)
                if (perspective <= 0f) continue

                val projX = center.x + x * perspective * baseScale
                val projY = center.y + yTilted * perspective * baseScale

                // Depth-based alpha and size
                val depthNormalized = ((zTilted + 120f) / 240f).coerceIn(0.15f, 1f)
                val pointAlpha = (p.alphaFactor * depthNormalized * (1f - scatter * 0.25f)).coerceIn(0.08f, 1f)
                val pointRadius = (p.size * perspective * (0.8f + depthNormalized * 0.4f)).coerceIn(0.8f, 5f)

                // Draw particle
                drawCircle(
                    color = accentColor.copy(alpha = pointAlpha),
                    radius = pointRadius,
                    center = Offset(projX, projY),
                )
            }
        }
    }
}
