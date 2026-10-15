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
import kotlin.random.Random

private class DnaParticle(
    val baseX: Float,
    val baseY: Float,
    val baseZ: Float,
    val size: Float,
    val scatterDirX: Float,
    val scatterDirY: Float,
    val scatterDirZ: Float,
    val alphaFactor: Float,
    val isRung: Boolean,
)

/**
 * 3D DNA Double Helix particle animation for "Моя волна".
 * Features two intertwined helical strands with connecting base pair rungs,
 * smooth 3D perspective projection, rhythmic pulse, and interactive particle burst.
 */
@Composable
fun SpiralParticleWave(
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    accentColor: Color = VotifyColors.Primary,
    isPlaying: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val totalParticles = 1400
    val particles = remember {
        val list = ArrayList<DnaParticle>(totalParticles)
        val random = Random(42)

        val height = 210f
        val radius = 54f
        val turns = 2.6f // Number of full helical twists

        // 1. Helical Backbone Strands (Strand A and Strand B)
        val strandParticles = 520
        for (i in 0 until strandParticles) {
            val t = i.toFloat() / strandParticles // 0..1
            val y = (t - 0.5f) * height
            val theta = t * (turns * 2f * PI.toFloat())

            // Strand A
            val jitterA = (random.nextFloat() - 0.5f) * 7f
            val rA = radius + jitterA
            val xA = rA * cos(theta)
            val zA = rA * sin(theta) + (random.nextFloat() - 0.5f) * 6f

            // Direction for scatter on tap
            val uA = random.nextFloat() * 2f * PI.toFloat()
            val vA = (random.nextFloat() - 0.5f) * PI.toFloat()
            val distA = 130f + random.nextFloat() * 150f

            list.add(
                DnaParticle(
                    baseX = xA,
                    baseY = y,
                    baseZ = zA,
                    size = 1.4f + random.nextFloat() * 1.8f,
                    scatterDirX = cos(vA) * cos(uA) * distA,
                    scatterDirY = sin(vA) * distA,
                    scatterDirZ = cos(vA) * sin(uA) * distA,
                    alphaFactor = 0.55f + random.nextFloat() * 0.45f,
                    isRung = false,
                )
            )

            // Strand B (180 degrees offset)
            val thetaB = theta + PI.toFloat()
            val jitterB = (random.nextFloat() - 0.5f) * 7f
            val rB = radius + jitterB
            val xB = rB * cos(thetaB)
            val zB = rB * sin(thetaB) + (random.nextFloat() - 0.5f) * 6f

            val uB = random.nextFloat() * 2f * PI.toFloat()
            val vB = (random.nextFloat() - 0.5f) * PI.toFloat()
            val distB = 130f + random.nextFloat() * 150f

            list.add(
                DnaParticle(
                    baseX = xB,
                    baseY = y,
                    baseZ = zB,
                    size = 1.4f + random.nextFloat() * 1.8f,
                    scatterDirX = cos(vB) * cos(uB) * distB,
                    scatterDirY = sin(vB) * distB,
                    scatterDirZ = cos(vB) * sin(uB) * distB,
                    alphaFactor = 0.55f + random.nextFloat() * 0.45f,
                    isRung = false,
                )
            )
        }

        // 2. Base Pair Rungs connecting Strand A and Strand B
        val numRungs = 24
        val particlesPerRung = 15
        for (r in 0 until numRungs) {
            val t = (r + 0.5f) / numRungs
            val y = (t - 0.5f) * height
            val theta = t * (turns * 2f * PI.toFloat())

            val xA = radius * cos(theta)
            val zA = radius * sin(theta)

            val xB = radius * cos(theta + PI.toFloat())
            val zB = radius * sin(theta + PI.toFloat())

            for (p in 0 until particlesPerRung) {
                val frac = (p + 1f) / (particlesPerRung + 1f)
                val rx = xA + (xB - xA) * frac + (random.nextFloat() - 0.5f) * 3f
                val ry = y + (random.nextFloat() - 0.5f) * 3f
                val rz = zA + (zB - zA) * frac + (random.nextFloat() - 0.5f) * 3f

                val u = random.nextFloat() * 2f * PI.toFloat()
                val v = (random.nextFloat() - 0.5f) * PI.toFloat()
                val dist = 120f + random.nextFloat() * 140f

                list.add(
                    DnaParticle(
                        baseX = rx,
                        baseY = ry,
                        baseZ = rz,
                        size = 1.1f + random.nextFloat() * 1.3f,
                        scatterDirX = cos(v) * cos(u) * dist,
                        scatterDirY = sin(v) * dist,
                        scatterDirZ = cos(v) * sin(u) * dist,
                        alphaFactor = 0.4f + random.nextFloat() * 0.4f,
                        isRung = true,
                    )
                )
            }
        }

        list
    }

    // Continuous 3D rotation transition around the helix axis
    val infiniteTransition = rememberInfiniteTransition(label = "dna3d")
    val rotationSpeed = if (isPlaying) 10_000 else 20_000
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dna_rotation",
    )

    // Gentle pulse animation
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 900 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dna_pulse",
    )

    // Scatter / Gather transition on tap
    val scatterAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isScattered by remember { mutableStateOf(false) }

    fun triggerScatter() {
        scope.launch {
            if (scatterAnim.isRunning) return@launch
            if (!isScattered) {
                isScattered = true
                scatterAnim.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
                delay(350)
                scatterAnim.animateTo(0f, animationSpec = tween(850, easing = FastOutSlowInEasing))
                isScattered = false
            } else {
                scatterAnim.animateTo(0f, animationSpec = tween(750, easing = FastOutSlowInEasing))
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
            val baseScale = (this.size.minDimension / 300f) * pulseScale
            val scatter = scatterAnim.value
            val rotRad = Math.toRadians(rotationAngle.toDouble()).toFloat()

            // Slight tilt of the DNA axis (e.g. 24 degrees) so the double helix angles aesthetically
            val tiltAngle = Math.toRadians(24.0).toFloat()
            val cosTilt = cos(tiltAngle)
            val sinTilt = sin(tiltAngle)

            // Radial ambient glow behind the DNA spiral
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.20f * (1f - scatter * 0.5f)),
                        accentColor.copy(alpha = 0.05f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = 95f * baseScale,
                ),
                radius = 95f * baseScale,
                center = center,
            )

            // 3D transformation and projection for each DNA particle
            val cameraDistance = 380f

            for (p in particles) {
                // 1. Rotation around the vertical Y axis
                val cosR = cos(rotRad)
                val sinR = sin(rotRad)
                var xRot = p.baseX * cosR - p.baseZ * sinR
                var yRot = p.baseY
                var zRot = p.baseX * sinR + p.baseZ * cosR

                // 2. Apply scatter burst offset
                if (scatter > 0f) {
                    xRot += p.scatterDirX * scatter
                    yRot += p.scatterDirY * scatter
                    zRot += p.scatterDirZ * scatter
                }

                // 3. Tilt the DNA axis slightly in 3D
                val xFinal = xRot * cosTilt - yRot * sinTilt
                val yFinal = xRot * sinTilt + yRot * cosTilt
                val zFinal = zRot

                // 4. Perspective projection
                val perspective = cameraDistance / (cameraDistance - zFinal)
                if (perspective <= 0f) continue

                val projX = center.x + xFinal * perspective * baseScale
                val projY = center.y + yFinal * perspective * baseScale

                // 5. Depth shading
                val depthNorm = ((zFinal + 70f) / 140f).coerceIn(0.18f, 1f)
                val particleAlpha = (p.alphaFactor * depthNorm * (1f - scatter * 0.25f)).coerceIn(0.08f, 1f)
                val particleRadius = (p.size * perspective * (0.8f + depthNorm * 0.35f)).coerceIn(0.7f, 4.5f)

                // Rungs have a slightly whiter luminous tint, backbone strands take the accent color
                val particleColor = if (p.isRung) {
                    Color.White.copy(alpha = particleAlpha * 0.85f)
                } else {
                    accentColor.copy(alpha = particleAlpha)
                }

                drawCircle(
                    color = particleColor,
                    radius = particleRadius,
                    center = Offset(projX, projY),
                )
            }
        }
    }
}
