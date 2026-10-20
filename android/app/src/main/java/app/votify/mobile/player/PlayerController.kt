package app.votify.mobile.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.votify.mobile.data.Track
import com.google.common.util.concurrent.MoreExecutors
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import app.votify.mobile.widget.WidgetUpdateHelper
import kotlin.random.Random
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val current: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val pitchLinked: Boolean = true,
    val reverbPreset: String = "none",
    val bassBoost: Boolean = false,
    val error: String? = null,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val hasNext: Boolean get() = queue.isNotEmpty()
}

/**
 * Single app-wide bridge between Compose UI and the MediaSession in [PlaybackService].
 * Tracks are turned into MediaItems that point at the backend's /api/stream proxy.
 */
/** Media items use this scheme; the ResolvingDataSource swaps it for the real stream URL. */
const val RESOLVE_URI_SCHEME = "votify://stream/"

/** Шаг плавного изменения громкости при кроссфейде. */
private const val FADE_STEP_MS = 60L

/**
 * Перемешивание без повторов: тасование Фишера-Йетса со своим Random.
 * Не использует kotlin.collections.shuffled по умолчанию, чтобы порядок не зависел
 * от общего источника случайности приложения.
 */
private val DISJOINT_SHUFFLE = Random(System.nanoTime())

class PlayerController(
    context: Context,
    private val scope: CoroutineScope,
    /** Called once per started media item — used to record listening history. */
    private val onTrackStarted: suspend (Track) -> Unit = {},
) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var lastReportedId: String? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    init {
        scope.launch {
            @OptIn(UnstableApi::class)
            AudioEffectsManager.reverbPreset.collect { r ->
                _state.update { it.copy(reverbPreset = r) }
            }
        }
        scope.launch {
            @OptIn(UnstableApi::class)
            AudioEffectsManager.bassBoostEnabled.collect { b ->
                _state.update { it.copy(bassBoost = b) }
            }
        }
    }

    private var controller: MediaController? = null
    private var queueTracks: List<Track> = emptyList()

    // ---- Перемешивание ----
    // Порядок задаём сами, а не встроенным shuffleMode ExoPlayer: так мы точно знаем,
    // какие треки уже играли, и не повторяем их, сколько бы раз ни нажимали кнопку.
    private var baseOrder: List<Track> = emptyList()
    private var shuffleOn: Boolean = false
    private val playedIds = LinkedHashSet<String>()

    // ---- Кроссфейд ----
    private var crossfadeEnabled: Boolean = false
    private var crossfadeSeconds: Int = 4
    private var fading = false
    /** Шаг фейда, живущий на главном потоке (обращения к плееру — только оттуда). */
    private var fadeRunnable: Runnable? = null

    /**
     * Настройки плавного перехода между треками (Настройки → Аудио).
     * Передаётся из настроек при их изменении.
     */
    fun setCrossfade(enabled: Boolean, seconds: Int) {
        crossfadeEnabled = enabled
        crossfadeSeconds = seconds.coerceIn(1, 12)
        if (!enabled) volumeTo(1f)
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer(player)
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                // Новый трек начинается с тишины и плавно нарастает — это вторая половина кроссфейда.
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) && crossfadeEnabled) {
                    startFadeIn()
                }
                // Пауза/остановка посреди затухания не должна оставлять громкость на нуле —
                // иначе после возобновления трек играл бы вслепую тихо.
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) {
                    cancelFade()
                    fading = false
                    volumeTo(1f)
                }
                reportStartedIfNeeded(player)
                WidgetUpdateHelper.updateAll(appContext)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Один неоткрывшийся трек не должен обрывать всю очередь: переходим
            // к следующему. Раньше здесь только записывалась ошибка, и воспроизведение
            // останавливалось насмерть.
            val p = controller
            val canContinue = p != null && p.isConnected && p.hasNextMediaItem()
            if (canContinue) {
                _state.update { it.copy(error = null, isBuffering = true) }
                runCatching {
                    p!!.seekToNextMediaItem()
                    if (!p.playWhenReady) p.play()
                }
                return
            }
            _state.update { it.copy(error = error.localizedMessage ?: "Playback error", isBuffering = false) }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            controller?.let { p ->
                if (p.isConnected && p.isPlaying) {
                    _state.update { it.copy(positionMs = p.currentPosition, durationMs = p.duration.coerceAtLeast(0)) }
                    // Затухание перед концом трека: следующий включится с нарастанием (см. startFadeIn).
                    maybeStartFadeOut(p)
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    /**
     * Плавно гасит громкость за [crossfadeSeconds] до конца трека и поднимает её
     * у следующего — настоящий слышимый переход без рывка. Общий аудиотракт один
     * (ExoPlayer не умеет два одновременно), поэтому треки сменяются встык.
     */
    private fun maybeStartFadeOut(p: MediaController) {
        if (!crossfadeEnabled || fading) return
        val total = p.duration
        if (total <= 0) return
        val fadeMs = crossfadeSeconds * 1000L
        val remaining = total - p.currentPosition
        // Не запускаем затухание на коротких треках: иначе он замолчит почти сразу.
        if (remaining in 1..fadeMs && total > fadeMs * 2) startFadeOut(fadeMs)
    }

    private fun startFadeOut(durationMs: Long) {
        val c = controller ?: return
        if (!c.isConnected) return
        cancelFade()
        fading = true
        val steps = (durationMs / FADE_STEP_MS).toInt().coerceAtLeast(1)
        var i = steps
        val step = object : Runnable {
            override fun run() {
                volumeTo(i / steps.toFloat())
                if (i <= 0) {
                    fading = false
                    fadeRunnable = null
                    return
                }
                i--
                handler.postDelayed(this, FADE_STEP_MS)
            }
        }
        fadeRunnable = step
        handler.post(step)
    }

    private fun startFadeIn() {
        val c = controller ?: return
        if (!c.isConnected) return
        cancelFade()
        val steps = (crossfadeSeconds * 1000L / FADE_STEP_MS).toInt().coerceAtLeast(1)
        var i = 0
        val step = object : Runnable {
            override fun run() {
                volumeTo(i / steps.toFloat())
                if (i >= steps) {
                    volumeTo(1f)
                    fadeRunnable = null
                    return
                }
                i++
                handler.postDelayed(this, FADE_STEP_MS)
            }
        }
        fadeRunnable = step
        handler.post(step)
    }

    /** Снять запланированные шаги фейда. */
    private fun cancelFade() {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        fadeRunnable = null
    }

    /**
     * Громкость меняем ТОЛЬКО с главного потока: MediaController в Media3 бросает
     * IllegalStateException при обращении из фонового потока ("method is called from
     * a wrong thread"). Именно из-за этого приложение падало при включении трека,
     * когда кроссфейд был включён.
     */
    private fun volumeTo(value: Float) {
        val c = controller ?: return
        if (!c.isConnected) return
        runCatching {
            if (c.availableCommands.contains(Player.COMMAND_SET_VOLUME)) {
                c.volume = value.coerceIn(0f, 1f)
            }
        }
    }

    fun connect(onConnected: ((MediaController) -> Unit)? = null) {
        val existing = controller
        if (existing != null && existing.isConnected) {
            onConnected?.invoke(existing)
            return
        }
        if (existing != null) {
            runCatching { existing.removeListener(listener) }
            runCatching { existing.release() }
            controller = null
        }
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (this@PlayerController.controller == controller) {
                        this@PlayerController.controller = null
                    }
                }
            })
            .buildAsync()
        future.addListener({
            runCatching { future.get() }.onSuccess { c ->
                controller = c
                c.addListener(listener)
                syncFromPlayer(c)
                handler.post(ticker)
                onConnected?.invoke(c)
            }.onFailure {
                controller = null
            }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        handler.removeCallbacks(ticker)
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    // ---- commands ----

    /** Replace the queue with [tracks] and start from [startIndex]. */
    fun play(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        baseOrder = tracks
        // Новая очередь — история прослушанного начинается заново, иначе треки
        // из другого списка считались бы «уже игравшими».
        playedIds.clear()
        val order = if (shuffleOn) {
            // При перемешивании начинаем с того, что выбрал пользователь,
            // а остальные идут в перемешанном порядке без повторов.
            val start = tracks.getOrNull(startIndex)
            val rest = (tracks - listOfNotNull(start)).shuffled(DISJOINT_SHUFFLE)
            if (start != null) {
                playedIds.add(start.id)
                listOf(start) + rest
            } else rest
        } else tracks
        queueTracks = order
        val index = if (shuffleOn) 0 else startIndex
        lastReportedId = null // a fresh start always counts as a new listen
        _state.update { it.copy(error = null, isBuffering = true) }
        val c = controller
        if (c != null && c.isConnected) {
            c.setMediaItems(order.map(::toMediaItem), index.coerceIn(0, order.lastIndex), 0L)
            c.prepare()
            c.play()
        } else {
            connect { connected ->
                connected.setMediaItems(order.map(::toMediaItem), index.coerceIn(0, order.lastIndex), 0L)
                connected.prepare()
                connected.play()
            }
        }
    }

    fun playTrack(track: Track) = play(listOf(track))

    fun togglePlayPause() {
        val c = controller
        if (c == null || !c.isConnected) {
            connect { it.play() }
            return
        }
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    /** Stop playback and clear the active track/queue (e.g. when mini-player is swiped down). */
    fun dismiss() {
        val c = controller
        runCatching {
            c?.pause()
            c?.clearMediaItems()
        }
        queueTracks = emptyList()
        lastReportedId = null
        _state.update {
            it.copy(
                current = null,
                queue = emptyList(),
                currentIndex = -1,
                isPlaying = false,
                isBuffering = false,
                positionMs = 0,
                durationMs = 0,
                error = null,
            )
        }
    }

    fun next() {
        val c = controller?.takeIf { it.isConnected } ?: return
        // Свой порядок: переходим к следующему треку очереди.
        if (shuffleOn && c.currentMediaItemIndex >= c.mediaItemCount - 1) {
            // Список дошёл до конца — продолжаем новым проходом без уже игравших треков.
            val reshuffled = reshuffleQueue()
            if (reshuffled.isNotEmpty()) {
                c.setMediaItems(reshuffled.map(::toMediaItem), 0, 0L)
                c.prepare()
                c.play()
            }
            return
        }
        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
    }

    /**
     * Новый проход перемешивания без повторов: исключаем всё, что уже играло.
     * Когда непроигранных не осталось — начинаем новый круг (без подряд идущего повтора).
     */
    private fun reshuffleQueue(): List<Track> {
        val unplayed = baseOrder.filterNot { it.id in playedIds }
        val next = if (unplayed.isNotEmpty()) {
            unplayed.shuffled(DISJOINT_SHUFFLE)
        } else {
            // Круг закончен: очищаем историю, но не даём первому треку совпасть с последним.
            // Текущий трек может отсутствовать в базовом списке (его добавили в очередь
            // отдельно) — поэтому только firstOrNull, без исключения.
            val last = queueTracks.lastOrNull()?.id
            playedIds.clear()
            val pool = baseOrder.filterNot { it.id == last }
            val shuffled = pool.shuffled(DISJOINT_SHUFFLE)
            if (last != null) {
                val tail = baseOrder.firstOrNull { it.id == last }
                if (tail != null) shuffled + tail else shuffled
            } else shuffled
        }
        if (next.isEmpty()) return emptyList()
        next.forEach { playedIds.add(it.id) }
        queueTracks = next
        _state.update { it.copy(queue = next) }
        return next
    }

    fun previous() {
        val c = controller?.takeIf { it.isConnected } ?: return
        // Standard behaviour: restart the track if we're past 3s, otherwise go back.
        if (c.currentPosition > 3_000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(fraction: Float) {
        val c = controller?.takeIf { it.isConnected } ?: return
        val d = c.duration
        if (d > 0) c.seekTo((d * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun seekToMs(positionMs: Long) {
        val c = controller?.takeIf { it.isConnected } ?: return
        c.seekTo(positionMs.coerceAtLeast(0))
        if (!c.isPlaying) c.play()
    }

    fun toggleShuffle() {
        val c = controller?.takeIf { it.isConnected } ?: return
        setShuffle(!shuffleOn)
    }

    /** Включить/выключить перемешивание без повторов (без необходимости в живом плеере). */
    fun setShuffle(enabled: Boolean) {
        if (shuffleOn == enabled) return
        shuffleOn = enabled
        _state.update { it.copy(shuffle = enabled) }
        // Порядок задаём сами: встроенный режим ExoPlayer держим выключенным,
        // иначе треки повторялись бы.
        val c = controller?.takeIf { it.isConnected } ?: return
        runCatching { c.shuffleModeEnabled = false }
        if (enabled) {
            // Перемешиваем оставшиеся треки, не трогая текущий и уже сыгранные.
            val currentId = c.currentMediaItem?.mediaId
            val head = queueTracks.take(c.currentMediaItemIndex + 1)
            val tail = baseOrder
                .filterNot { t -> t.id in playedIds || head.any { it.id == t.id } }
                .shuffled(DISJOINT_SHUFFLE)
            val newQueue = head + tail
            if (newQueue.isNotEmpty()) {
                queueTracks = newQueue
                tail.forEach { playedIds.add(it.id) }
                c.setMediaItems(

                    newQueue.map(::toMediaItem),
                    c.currentMediaItemIndex.coerceIn(0, newQueue.lastIndex),
                    c.currentPosition,
                )
                c.prepare()
                c.play()
                _state.update { it.copy(queue = newQueue) }
            }
        } else {
            // Возврат к исходному порядку с сохранением текущего трека.
            val currentId = c.currentMediaItem?.mediaId
            val current = queueTracks.firstOrNull { it.id == currentId }
            val newQueue = if (current != null) {
                listOf(current) + baseOrder.filterNot { it.id == currentId }
            } else baseOrder
            if (newQueue.isNotEmpty()) {
                queueTracks = newQueue
                val idx = newQueue.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
                c.setMediaItems(newQueue.map(::toMediaItem), idx, c.currentPosition)
                c.prepare()
                c.play()
                _state.update { it.copy(queue = newQueue) }
            }
        }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Append tracks to the end of the current queue (or start playing if idle). */
    fun enqueue(tracks: List<Track>) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        if (c.mediaItemCount == 0) {
            play(tracks)
            return
        }
        val existing = queueTracks.map { it.id }.toSet()
        val fresh = tracks.filterNot { it.id in existing }
        if (fresh.isEmpty()) return
        queueTracks = queueTracks + fresh
        // Базовый список тоже пополняем, иначе перемешивание про эти треки не знает
        // и они выпадают из очереди при переключении.
        baseOrder = baseOrder + fresh.filterNot { t -> baseOrder.any { it.id == t.id } }
        c.addMediaItems(fresh.map(::toMediaItem))
        _state.update { it.copy(queue = queueTracks) }
    }

    /** Insert a track right after the current one. */
    fun playNext(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(listOf(track))
            return
        }
        val at = c.currentMediaItemIndex + 1
        val mutable = queueTracks.toMutableList()
        mutable.add(at.coerceAtMost(mutable.size), track)
        queueTracks = mutable
        // Порядок воспроизведения и базовый список держим согласованными.
        val baseAt = baseOrder.indexOfFirst { it.id == c.currentMediaItem?.mediaId }.let { if (it < 0) baseOrder.size else it + 1 }
        baseOrder = baseOrder.toMutableList().apply { add(baseAt.coerceAtMost(baseOrder.size), track) }
        c.addMediaItem(at, toMediaItem(track))
        _state.update { it.copy(queue = queueTracks) }
    }

    fun skipToQueueItem(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    private fun reportStartedIfNeeded(p: Player) {
        if (!p.isPlaying) return
        val id = p.currentMediaItem?.mediaId ?: return
        if (id == lastReportedId) return
        lastReportedId = id
        val track = queueTracks.firstOrNull { it.id == id } ?: return
        scope.launch { runCatching { onTrackStarted(track) } }
    }

    // ---- internals ----

    private fun toMediaItem(t: Track): MediaItem {
        val rawUri = if (t.id.startsWith("content://") || t.id.startsWith("file://")) {
            Uri.parse(t.id)
        } else if (t.id.startsWith("local:")) {
            Uri.parse(t.id.removePrefix("local:"))
        } else {
            Uri.parse(RESOLVE_URI_SCHEME + Uri.encode(t.id))
        }

        val builder = MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(rawUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setArtworkUri(t.cover.takeIf { it.isNotBlank() }?.toUri())
                    .build(),
            )

        val uriStr = rawUri.toString().lowercase()
        val titleStr = t.title.lowercase()
        val idStr = t.id.lowercase()
        if (uriStr.endsWith(".flac") || titleStr.endsWith(".flac") || idStr.contains(".flac")) {
            builder.setMimeType(MimeTypes.AUDIO_FLAC)
        } else if (uriStr.endsWith(".mp3") || titleStr.endsWith(".mp3") || idStr.contains(".mp3")) {
            builder.setMimeType(MimeTypes.AUDIO_MPEG)
        } else if (uriStr.endsWith(".wav") || titleStr.endsWith(".wav") || idStr.contains(".wav")) {
            builder.setMimeType(MimeTypes.AUDIO_WAV)
        } else if (uriStr.endsWith(".m4a") || uriStr.endsWith(".aac") || idStr.contains(".m4a")) {
            builder.setMimeType(MimeTypes.AUDIO_AAC)
        } else if (uriStr.endsWith(".ogg") || uriStr.endsWith(".opus") || idStr.contains(".ogg")) {
            builder.setMimeType(MimeTypes.AUDIO_OGG)
        }

        return builder.build()
    }

    private fun syncFromPlayer(p: Player) {
        val id = p.currentMediaItem?.mediaId
        val current = queueTracks.firstOrNull { it.id == id }
            ?: p.currentMediaItem?.let { mi ->
                Track(
                    id = mi.mediaId,
                    title = mi.mediaMetadata.title?.toString() ?: "",
                    artist = mi.mediaMetadata.artist?.toString() ?: "",
                    cover = mi.mediaMetadata.artworkUri?.toString() ?: "",
                )
            }
        _state.update {
            it.copy(
                current = current,
                queue = queueTracks,
                currentIndex = p.currentMediaItemIndex,
                isPlaying = p.isPlaying,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                positionMs = p.currentPosition.coerceAtLeast(0),
                durationMs = p.duration.coerceAtLeast(0),
                // Не берём p.shuffleModeEnabled: встроенный режим мы держим выключенным,
                // порядок задаём сами. Иначе индикатор перемешивания мигал бы.
                shuffle = shuffleOn,
                repeatMode = p.repeatMode,
                speed = p.playbackParameters.speed,
                pitch = p.playbackParameters.pitch,
            )
        }
    }

    /**
     * Настройки аудио: безшовность (только из сервиса), нормализация громкости,
     * и поведение при уведомлении.
     */
    fun requestAudioPreferences(gapless: Boolean, normalization: Boolean, pauseOnDuck: Boolean) {
        PlaybackService.applyAudioPrefs(
            gapless = gapless,
            normalization = normalization,
            pauseOnDuck = pauseOnDuck,
        )
        // «При уведомлении» можно применить и на живом плеере: setAudioAttributes
        // доступен через контроллер (Media3), в отличие от параметров Builder'а.
        runCatching {
            val c = controller?.takeIf { it.isConnected } ?: return@runCatching
            val attrs = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setFlags(pauseOnDuckFlag(pauseOnDuck))
                .build()
            c.setAudioAttributes(attrs, /* handleAudioFocus = */ true)
        }
    }

    /** Флаг «на паузу вместо приглушения»: 4 = AudioAttributes.FLAG_PAUSE_WHEN_DUCKED. */
    private fun pauseOnDuckFlag(pauseOnDuck: Boolean): Int = if (pauseOnDuck) 4 else 0

    fun setPlaybackRemix(speed: Float, pitch: Float? = null, pitchLinked: Boolean = _state.value.pitchLinked) {
        val s = (Math.round(speed * 100f) / 100f).coerceIn(0.25f, 2.5f)
        val p = if (pitchLinked) s else ((Math.round((pitch ?: s) * 100f) / 100f).coerceIn(0.25f, 2.5f))
        controller?.takeIf { it.isConnected }?.setPlaybackParameters(PlaybackParameters(s, p))
        _state.update { it.copy(speed = s, pitch = p, pitchLinked = pitchLinked) }
    }

    fun setPlaybackSpeed(speed: Float) {
        setPlaybackRemix(speed = speed)
    }

    fun setPlaybackPitch(pitch: Float) {
        setPlaybackRemix(speed = _state.value.speed, pitch = pitch, pitchLinked = false)
    }

    fun setPitchLinked(linked: Boolean) {
        val s = _state.value.speed
        val p = if (linked) s else _state.value.pitch
        controller?.takeIf { it.isConnected }?.setPlaybackParameters(PlaybackParameters(s, p))
        _state.update { it.copy(pitchLinked = linked, pitch = p) }
    }

    @OptIn(UnstableApi::class)
    fun setReverb(preset: String) {
        AudioEffectsManager.setReverb(preset)
        _state.update { it.copy(reverbPreset = preset) }
    }

    @OptIn(UnstableApi::class)
    fun setBassBoost(enabled: Boolean) {
        AudioEffectsManager.setBassBoost(enabled)
        _state.update { it.copy(bassBoost = enabled) }
    }

    @OptIn(UnstableApi::class)
    fun setEqualizerBands(bands: List<Float>) {
        AudioEffectsManager.setEqualizerBands(bands)
    }
}
