package app.votify.mobile.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.util.Log
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages native audio effects (PresetReverb for atmospheric "Slowed + Reverb" soundstage,
 * BassBoost for deep club/subwoofer bass, and Equalizer) synchronized with ExoPlayer's audio session.
 */
@UnstableApi
object AudioEffectsManager {
    private const val TAG = "AudioEffectsManager"

    private var player: ExoPlayer? = null
    private var presetReverb: PresetReverb? = null
    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var normalizationEnabled: Boolean = false
    private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    private val _reverbPreset = MutableStateFlow("none")
    val reverbPreset: StateFlow<String> = _reverbPreset.asStateFlow()

    private val _bassBoostEnabled = MutableStateFlow(false)
    val bassBoostEnabled: StateFlow<Boolean> = _bassBoostEnabled.asStateFlow()

    private val _equalizerBands = MutableStateFlow(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
    val equalizerBands: StateFlow<List<Float>> = _equalizerBands.asStateFlow()

    fun attachPlayer(exoPlayer: ExoPlayer) {
        player = exoPlayer
        currentAudioSessionId = exoPlayer.audioSessionId
        applyEffects()
    }

    fun onAudioSessionIdChanged(audioSessionId: Int) {
        currentAudioSessionId = audioSessionId
        releaseBass()
        releaseEqualizer()
        releaseLoudness()
        applyBassBoost()
        applyEqualizer()
        applyNormalization()
    }

    fun detachPlayer() {
        runCatching { player?.clearAuxEffectInfo() }
        player = null
        releaseReverb()
        releaseBass()
        releaseEqualizer()
    }

    fun setReverb(preset: String) {
        _reverbPreset.value = preset
        applyReverb()
    }

    fun setBassBoost(enabled: Boolean) {
        _bassBoostEnabled.value = enabled
        applyBassBoost()
    }

    fun setEqualizerBands(bands: List<Float>) {
        _equalizerBands.value = bands.take(10)
        applyEqualizer()
    }

    /**
     * Нормализация громкости (Настройки → Аудио). Реально усиливает тихие места:
     * LoudnessEnhancer поднимает уровень на фиксированную величину в миллибелах.
     */
    fun setNormalization(enabled: Boolean) {
        normalizationEnabled = enabled
        applyNormalization()
    }

    private fun applyNormalization() {
        val sessionId = currentAudioSessionId
        if (!normalizationEnabled || sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) {
            runCatching { loudness?.enabled = false }
            return
        }
        runCatching {
            if (loudness == null) loudness = LoudnessEnhancer(sessionId)
            loudness?.let { le ->
                // 600 мБ ≈ заметная, но не искажающая прибавка громкости.
                le.setTargetGain(600)
                le.enabled = true
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to apply LoudnessEnhancer: ${e.message}")
        }
    }

    private fun applyEffects() {
        applyReverb()
        applyBassBoost()
        applyEqualizer()
        applyNormalization()
    }

    private fun applyReverb() {
        val p = player ?: return
        val preset = _reverbPreset.value
        if (preset == "none") {
            runCatching {
                p.clearAuxEffectInfo()
                presetReverb?.enabled = false
            }
            return
        }

        runCatching {
            if (presetReverb == null) {
                // Priority 0, Audio Session 0 for auxiliary effects
                presetReverb = PresetReverb(0, 0)
            }
            presetReverb?.let { reverb ->
                val presetCode = when (preset) {
                    "hall" -> PresetReverb.PRESET_LARGEHALL
                    "room" -> PresetReverb.PRESET_LARGEROOM
                    "plate" -> PresetReverb.PRESET_PLATE
                    else -> PresetReverb.PRESET_MEDIUMHALL
                }
                reverb.preset = presetCode
                reverb.enabled = true
                p.setAuxEffectInfo(AuxEffectInfo(reverb.id, 1.0f))
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to apply PresetReverb ($preset): ${e.message}")
        }
    }

    private fun applyBassBoost() {
        val sessionId = currentAudioSessionId
        val enabled = _bassBoostEnabled.value
        if (!enabled || sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) {
            runCatching {
                bassBoost?.enabled = false
            }
            return
        }

        runCatching {
            if (bassBoost == null) {
                bassBoost = BassBoost(0, sessionId)
            }
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(1000.toShort())
                }
                bb.enabled = true
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to apply BassBoost: ${e.message}")
        }
    }

    private fun applyEqualizer() {
        val sessionId = currentAudioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) {
            runCatching { equalizer?.enabled = false }
            return
        }
        val bands = _equalizerBands.value
        val isFlat = bands.all { kotlin.math.abs(it) < 0.05f }
        runCatching {
            if (equalizer == null) {
                equalizer = Equalizer(0, sessionId)
            }
            equalizer?.let { eq ->
                val numBands = eq.numberOfBands.toInt()
                if (numBands > 0) {
                    val bandRange = eq.bandLevelRange // [min, max] in millibels (e.g. -1500..1500)
                    val minMb = bandRange[0].toInt()
                    val maxMb = bandRange[1].toInt()
                    val userFreqs = floatArrayOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
                    for (b in 0 until numBands) {
                        val centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000f
                        val gainDb = interpolateGain(centerFreqHz, userFreqs, bands)
                        val millibels = (gainDb * 100).toInt().coerceIn(minMb, maxMb)
                        eq.setBandLevel(b.toShort(), millibels.toShort())
                    }
                    eq.enabled = !isFlat
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to apply Equalizer: ${e.message}")
        }
    }

    private fun interpolateGain(freq: Float, freqs: FloatArray, gains: List<Float>): Float {
        if (freq <= freqs.first()) return gains.firstOrNull() ?: 0f
        if (freq >= freqs.last()) return gains.lastOrNull() ?: 0f
        for (i in 0 until freqs.size - 1) {
            if (freq >= freqs[i] && freq <= freqs[i + 1]) {
                val denom = (kotlin.math.ln(freqs[i + 1]) - kotlin.math.ln(freqs[i])).coerceAtLeast(0.001f)
                val t = (kotlin.math.ln(freq) - kotlin.math.ln(freqs[i])) / denom
                val g0 = gains.getOrElse(i) { 0f }
                val g1 = gains.getOrElse(i + 1) { 0f }
                return g0 + t.toFloat() * (g1 - g0)
            }
        }
        return 0f
    }

    private fun releaseReverb() {
        runCatching {
            presetReverb?.enabled = false
            presetReverb?.release()
        }
        presetReverb = null
    }

    private fun releaseBass() {
        runCatching {
            bassBoost?.enabled = false
            bassBoost?.release()
        }
        bassBoost = null
    }

    private fun releaseEqualizer() {
        runCatching {
            equalizer?.enabled = false
            equalizer?.release()
        }
        equalizer = null
    }

    private fun releaseLoudness() {
        runCatching {
            loudness?.enabled = false
            loudness?.release()
        }
        loudness = null
    }
}
