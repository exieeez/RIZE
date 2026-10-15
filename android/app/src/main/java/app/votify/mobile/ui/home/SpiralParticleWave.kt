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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.votify.mobile.ui.theme.VotifyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

private class GalaxyParticle(
    val radius: Float,
    val baseAngle: Float,
    val zOffset: Float,
    val speedFactor: Float,
    val size: Float,
    val scatterDirX: Float,
    val scatterDirY: Float,
    val scatterDirZ: Float,
    val alphaFactor: Float,
    val isCore: Boolean,
    val colorVariant: Int, // 0 = white/bright, 1 = accent, 2 = soft cyan/violet
)

/**
 * Beautiful circular spiral galaxy particle animation for "Моя волна".
 * Features a glowing dense galactic core, 3 elegant spiral arms of glowing star dots,
 * differential galactic rotation, rhythmic music pulsation, and an interactive 3D burst on tap.
 */
@Composable
fun SpiralParticleWave(
    modifier: Modifier = Modifier,
    size: Dp = 330.dp,
    accentColor: Color = VotifyColors.Primary,
    isPlaying: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val totalParticles = 2200
    val particles = remember {
        val list = ArrayList<GalaxyParticle>(totalParticles)
        val random = Random(1337)

        val maxRadius = 145f
        val coreRadius = 36f
        val numArms = 2

        // 1. Dense Galactic Bulge / Core (520 particles)
        val coreCount = 520
        for (i in 0 until coreCount) {
            val r = sqrt(random.nextFloat()) * coreRadius
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val z = (random.nextFloat() - 0.5f) * 32f * (1f - r / coreRadius)

            val u = random.nextFloat() * 2f * PI.toFloat()
            val v = (random.nextFloat() - 0.5f) * PI.toFloat()
            val dist = 120f + random.nextFloat() * 150f

            list.add(
                GalaxyParticle(
                    radius = r,
                    baseAngle = angle,
                    zOffset = z,
                    speedFactor = 1.25f - (r / coreRadius) * 0.25f,
                    size = 1.2f + random.nextFloat() * 2.0f,
                    scatterDirX = cos(v) * cos(u) * dist,
                    scatterDirY = sin(v) * dist,
                    scatterDirZ = cos(v) * sin(u) * dist,
                    alphaFactor = 0.70f + random.nextFloat() * 0.30f,
                    isCore = true,
                    colorVariant = if (random.nextFloat() < 0.65f) 0 else 1,
                )
            )
        }

        // 2. Logarithmic Spiral Arms (1380 particles)
        val armCount = 1380
        for (i in 0 until armCount) {
            val armIndex = i % numArms
            val armBaseAngle = armIndex * (2f * PI.toFloat() / numArms)

            // Radius along arm with slightly higher density towards center
            val t = random.nextFloat()
            val r = coreRadius + t * (maxRadius - coreRadius)

            // Spiral angle equation: theta = theta_0 + alpha * r^0.75
            val spiralTheta = armBaseAngle + 3.2f * (r / maxRadius).pow(0.75f)

            // Arm spread / dispersion (Gaussian-like spread perpendicular to arm)
            val r1 = random.nextFloat() - 0.5f
            val r2 = random.nextFloat() - 0.5f
            val dispersion = (r1 + r2) * 0.40f * (1f + (r / maxRadius) * 0.5f)
            val finalAngle = spiralTheta + dispersion

            // Disk thickness tapering towards edges for true 3D volume
            val diskThickness = (1f - (r / maxRadius) * 0.35f) * 28f
            val z = (random.nextFloat() - 0.5f) * diskThickness

            val u = random.nextFloat() * 2f * PI.toFloat()
            val v = (random.nextFloat() - 0.5f) * PI.toFloat()
            val dist = 130f + random.nextFloat() * 170f

            val variantRoll = random.nextFloat()
            val variant = when {
                variantRoll < 0.35f -> 0 // Bright stellar point
                variantRoll < 0.80f -> 1 // Primary accent
                else -> 2 // Soft luminous secondary
            }

            list.add(
                GalaxyParticle(
                    radius = r,
                    baseAngle = finalAngle,
                    zOffset = z,
                    // Flat rotation curve: v ~ constant -> omega = v / r
                    speedFactor = 0.90f + (1f - r / maxRadius) * 0.45f,
                    size = 1.0f + random.nextFloat() * 2.2f,
                    scatterDirX = cos(v) * cos(u) * dist,
                    scatterDirY = sin(v) * dist,
                    scatterDirZ = cos(v) * sin(u) * dist,
                    alphaFactor = 0.45f + random.nextFloat() * 0.50f,
                    isCore = false,
                    colorVariant = variant,
                )
            )
        }

        // 3. Ambient Stellar Halo (300 particles scattered randomly across the circular disk)
        val haloCount = 300
        for (i in 0 until haloCount) {
            val r = sqrt(random.nextFloat()) * maxRadius
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val z = (random.nextFloat() - 0.5f) * 26f

            val u = random.nextFloat() * 2f * PI.toFloat()
            val v = (random.nextFloat() - 0.5f) * PI.toFloat()
            val dist = 110f + random.nextFloat() * 150f

            list.add(
                GalaxyParticle(
                    radius = r,
                    baseAngle = angle,
                    zOffset = z,
                    speedFactor = 0.85f,
                    size = 0.9f + random.nextFloat() * 1.5f,
                    scatterDirX = cos(v) * cos(u) * dist,
                    scatterDirY = sin(v) * dist,
                    scatterDirZ = cos(v) * sin(u) * dist,
                    alphaFactor = 0.30f + random.nextFloat() * 0.35f,
                    isCore = false,
                    colorVariant = if (random.nextFloat() < 0.5f) 0 else 1,
                )
            )
        }

        list
    }

    // Continuous orbital rotation of the galaxy
    val infiniteTransition = rememberInfiniteTransition(label = "galaxyRot")
    val rotationPeriod = if (isPlaying) 14_000 else 24_000
    val globalRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationPeriod, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "galaxy_rot",
    )

    // Gentle musical pulse / breathing animation
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 1000 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "galaxy_pulse",
    )

    // Scatter / Re-gather burst transition on tap
    val scatterAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isScattered by remember { mutableStateOf(false) }

    fun triggerScatter() {
        scope.launch {
            if (scatterAnim.isRunning) return@launch
            if (!isScattered) {
                isScattered = true
                scatterAnim.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
                delay(300)
                scatterAnim.animateTo(0f, animationSpec = tween(850, easing = FastOutSlowInEasing))
                isScattered = false
            } else {
                scatterAnim.animateTo(0f, animationSpec = tween(700, easing = FastOutSlowInEasing))
                isScattered = false
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clickable(interactionSource = interactionSource, indication = null) {
                triggerScatter()
                onClick?.invoke()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseScale = (this.size.minDimension / 310f) * pulseScale
            val scatter = scatterAnim.value
            val globalRad = Math.toRadians(globalRotation.toDouble()).toFloat()

            // 3D tilt of the galaxy plane (30 degrees) for deep volumetric perspective
            val tiltAngle = Math.toRadians(30.0).toFloat()
            val cosTilt = cos(tiltAngle)
            val sinTilt = sin(tiltAngle)

            // Luminous ambient cosmic glow behind the galactic nucleus
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.38f * (1f - scatter * 0.4f)),
                        accentColor.copy(alpha = 0.32f * (1f - scatter * 0.4f)),
                        accentColor.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = 95f * baseScale,
                ),
                radius = 95f * baseScale,
                center = center,
            )

            // Outer soft aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.15f * (1f - scatter * 0.3f)),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = 155f * baseScale,
                ),
                radius = 155f * baseScale,
                center = center,
            )

            val cameraDistance = 440f

            for (p in particles) {
                // Differential rotation: each star orbits smoothly
                val currentAngle = p.baseAngle + globalRad * p.speedFactor
                var x = p.radius * cos(currentAngle)
                var y = p.radius * sin(currentAngle)
                var z = p.zOffset

                // Apply interactive 3D burst scatter
                if (scatter > 0f) {
                    x += p.scatterDirX * scatter
                    y += p.scatterDirY * scatter
                    z += p.scatterDirZ * scatter
                }

                // 3D perspective projection
                val yTilted = y * cosTilt - z * sinTilt
                val zTilted = y * sinTilt + z * cosTilt

                val perspective = cameraDistance / (cameraDistance - zTilted)
                if (perspective <= 0f) continue

                val projX = center.x + x * perspective * baseScale
                val projY = center.y + yTilted * perspective * baseScale

                val depthNorm = ((zTilted + 40f) / 80f).coerceIn(0.3f, 1f)
                val alpha = (p.alphaFactor * depthNorm * (1f - scatter * 0.2f)).coerceIn(0.08f, 1f)
                val radiusPx = (p.size * perspective * (0.85f + depthNorm * 0.25f)).coerceIn(0.6f, 4.0f)

                val color = when (p.colorVariant) {
                    0 -> Color.White.copy(alpha = alpha)
                    1 -> accentColor.copy(alpha = alpha)
                    else -> Color(0xFF80D8FF).copy(alpha = alpha * 0.90f) // Soft stellar cyan
                }

                drawCircle(
                    color = color,
                    radius = radiusPx,
                    center = Offset(projX, projY),
                )
            }
        }
    }
}
