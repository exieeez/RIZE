package app.votify.mobile.ui.library

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.LibraryRepository
import app.votify.mobile.data.Track
import app.votify.mobile.data.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImportUiState(
    val url: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** Non-empty after a successful import: tracks + editable playlist name. */
    val tracks: List<Track> = emptyList(),
    val name: String = "",
    val saving: Boolean = false,
)

sealed interface ImportEvent {
    data class Imported(val playlistId: Long, val name: String, val count: Int, val total: Int) : ImportEvent
}

/**
 * «Импортировать из сервиса»: paste a YouTube / Spotify / SoundCloud playlist URL, the
 * backend resolves it to tracks (GET /api/playlist or /api/soundcloud/import), then the
 * result is saved as a local playlist in Room.
 */
class ImportViewModel(
    private val music: MusicRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state

    private val _events = MutableSharedFlow<ImportEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<ImportEvent> = _events

    fun onUrlChange(v: String) = _state.update { it.copy(url = v) }

    fun onNameChange(v: String) = _state.update { it.copy(name = v.take(80)) }

    fun import() {
        val url = _state.value.url.trim()
        if (url.isEmpty() || _state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, tracks = emptyList(), name = "") }
            runCatching { music.importPlaylist(url) }
                .onSuccess { imported ->
                    _state.update {
                        it.copy(
                            loading = false,
                            tracks = imported.tracks,
                            name = imported.name?.takeIf { n -> n.isNotBlank() } ?: defaultName(),
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(loading = false, error = e.message ?: "Import failed") }
                }
        }
    }

    /** Create the local playlist and add every imported track to it. */
    fun saveToLibrary() {
        val s = _state.value
        if (s.tracks.isEmpty() || s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val name = s.name.trim().ifEmpty { defaultName() }
            val id = library.createPlaylist(name)
            var added = 0
            s.tracks.forEach { t -> if (library.addToPlaylist(id, t)) added++ }
            _state.update { it.copy(saving = false, url = "", tracks = emptyList(), name = "") }
            _events.tryEmit(ImportEvent.Imported(id, name, added, s.tracks.size))
        }
    }

    /**
     * Import user's local audio files (.mp3, .wav, .flac, .m4a):
     * Audio is played locally from storage, while high-res artwork and metadata are fetched from outside.
     */
    fun importLocalTracks(uris: List<Uri>, context: Context) {
        if (uris.isEmpty() || _state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, tracks = emptyList(), name = "Мои треки") }
            val loadedTracks = withContext(Dispatchers.IO) {
                val list = mutableListOf<Track>()
                for (uri in uris) {
                    try {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }

                        var rawTitle: String? = null
                        var rawArtist: String? = null
                        var durationMs: Long = 0

                        val mmr = MediaMetadataRetriever()
                        try {
                            mmr.setDataSource(context, uri)
                            rawTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            rawArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            durationMs = durStr?.toLongOrNull() ?: 0L
                        } catch (_: Exception) {
                        } finally {
                            runCatching { mmr.release() }
                        }

                        if (rawTitle.isNullOrBlank()) {
                            val cursor = context.contentResolver.query(uri, null, null, null, null)
                            cursor?.use { c ->
                                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameIndex >= 0 && c.moveToFirst()) {
                                    val fileName = c.getString(nameIndex)
                                    val cleanName = fileName.substringBeforeLast(".")
                                    if (" - " in cleanName) {
                                        val parts = cleanName.split(" - ", limit = 2)
                                        rawArtist = parts[0].trim()
                                        rawTitle = parts[1].trim()
                                    } else {
                                        rawTitle = cleanName
                                    }
                                }
                            }
                        }

                        val cleanTitle = rawTitle?.takeIf { it.isNotBlank() } ?: "Трек"
                        val cleanArtist = rawArtist?.takeIf { it.isNotBlank() } ?: ""
                        val durationSec = (durationMs / 1000).toInt()

                        var extCover = ""
                        var finalTitle = cleanTitle
                        var finalArtist = cleanArtist

                        val searchQuery = if (cleanArtist.isNotBlank()) "$cleanArtist $cleanTitle" else cleanTitle
                        try {
                            val encoded = URLEncoder.encode(searchQuery, "UTF-8")
                            val itunesUrl = URL("https://itunes.apple.com/search?term=$encoded&entity=song&limit=1")
                            val conn = (itunesUrl.openConnection() as HttpURLConnection).apply {
                                connectTimeout = 4000
                                readTimeout = 4000
                                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            }
                            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                            val root = JSONObject(jsonStr)
                            val results = root.optJSONArray("results")
                            if (results != null && results.length() > 0) {
                                val item = results.getJSONObject(0)
                                extCover = item.optString("artworkUrl100", "").replace("100x100bb", "600x600bb")
                                finalTitle = item.optString("trackName", cleanTitle)
                                finalArtist = item.optString("artistName", cleanArtist)
                            }
                        } catch (_: Exception) {
                        }

                        list.add(
                            Track(
                                id = "local:$uri",
                                title = finalTitle,
                                artist = finalArtist,
                                duration = durationSec,
                                cover = extCover,
                            )
                        )
                    } catch (_: Exception) {}
                }
                list
            }
            _state.update {
                it.copy(
                    loading = false,
                    tracks = loadedTracks,
                    name = "Мои треки (${loadedTracks.size})",
                    error = if (loadedTracks.isEmpty()) "Не удалось загрузить выбранные файлы" else null
                )
            }
        }
    }

    private fun defaultName(): String =
        SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date())
}
