package app.votify.mobile.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.votify.mobile.data.DeezerAlbum
import app.votify.mobile.data.DeezerApi
import app.votify.mobile.data.DeezerArtist
import app.votify.mobile.data.MusicRepository
import app.votify.mobile.data.Track
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchFilter(val label: String) {
    All("Всё"),
    Tracks("Треки"),
    Albums("Альбомы"),
    Artists("Артисты"),
}

data class PopularItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val cover: String,
    val type: String, // "Трек" | "Альбом" | "Артист"
    val track: Track? = null,
    val artistName: String = "",
    val albumId: Long = 0L,
)

data class HistoryItem(
    val query: String,
    val cover: String = "",
    val title: String = "",
    val subtitle: String = "",
    val track: Track? = null,
)

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.All,
    val results: List<Track> = emptyList(),
    val artistResults: List<DeezerArtist> = emptyList(),
    val albumResults: List<DeezerAlbum> = emptyList(),
    val popularItems: List<PopularItem> = emptyList(),
    val historyItems: List<HistoryItem> = emptyList(),
    val recent: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingPopular: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
    val serverMode: Boolean = true,
    val searched: Boolean = false,
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val music: MusicRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    private val queryFlow = MutableStateFlow("")
    private var job: Job? = null

    init {
        // Debounced search-as-you-type
        queryFlow
            .debounce(450)
            .map { it.trim() }
            .distinctUntilChanged()
            .filter { it.length >= 2 }
            .onEach { runSearch(it) }
            .launchIn(viewModelScope)

        // Load trending hits & releases on startup for "Популярные запросы"
        loadPopularContent()
    }

    private fun loadPopularContent() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPopular = true) }
            try {
                val chartTracks = DeezerApi.getChartTracks(limit = 12)
                val chartAlbums = DeezerApi.getChartAlbums(limit = 8)

                val items = mutableListOf<PopularItem>()
                for (t in chartTracks) {
                    items.add(
                        PopularItem(
                            id = "track_${t.id}",
                            title = t.title,
                            subtitle = t.artistName,
                            cover = t.albumCover,
                            type = "Трек",
                            track = t.toVotifyTrack(),
                        )
                    )
                }
                for (a in chartAlbums) {
                    items.add(
                        PopularItem(
                            id = "album_${a.id}",
                            title = a.title,
                            subtitle = a.artistName,
                            cover = a.coverBig.ifBlank { a.cover },
                            type = "Альбом",
                            artistName = a.artistName,
                            albumId = a.id,
                        )
                    )
                }

                _state.update { it.copy(popularItems = items, isLoadingPopular = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingPopular = false) }
            }
        }
    }

    fun setFilter(filter: SearchFilter) {
        _state.update { it.copy(filter = filter) }
        val q = _state.value.query.trim()
        if (q.isNotEmpty()) {
            runSearch(q)
        }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        if (q.isBlank()) {
            job?.cancel()
            _state.update {
                it.copy(
                    results = emptyList(),
                    artistResults = emptyList(),
                    albumResults = emptyList(),
                    isLoading = false,
                    error = null,
                    offline = false,
                    searched = false,
                )
            }
        }
        queryFlow.value = q
    }

    fun submit() {
        val q = _state.value.query.trim()
        if (q.isNotEmpty()) runSearch(q)
    }

    fun retry() = submit()

    fun clear() = onQueryChange("")

    fun pickRecent(q: String) {
        _state.update { it.copy(query = q) }
        runSearch(q)
    }

    fun clearRecent() = _state.update { it.copy(recent = emptyList(), historyItems = emptyList()) }

    fun addTrackToHistory(track: Track) {
        val item = HistoryItem(
            query = track.title,
            cover = track.cover,
            title = track.title,
            subtitle = track.artist,
            track = track,
        )
        _state.update {
            val updatedHistory = (listOf(item) + it.historyItems.filterNot { h -> h.title.equals(track.title, ignoreCase = true) }).take(10)
            val updatedRecent = (listOf(track.title) + it.recent.filterNot { r -> r.equals(track.title, ignoreCase = true) }).take(10)
            it.copy(historyItems = updatedHistory, recent = updatedRecent)
        }
    }

    private fun runSearch(q: String) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, offline = false, serverMode = music.isServerMode) }
            val currentFilter = _state.value.filter

            try {
                var tracksResult: List<Track> = emptyList()
                var artistsResult: List<DeezerArtist> = emptyList()
                var albumsResult: List<DeezerAlbum> = emptyList()

                when (currentFilter) {
                    SearchFilter.Artists -> {
                        artistsResult = DeezerApi.searchArtists(q, limit = 25)
                    }
                    SearchFilter.Albums -> {
                        albumsResult = DeezerApi.searchAlbums(q, limit = 25)
                    }
                    SearchFilter.Tracks -> {
                        tracksResult = music.search(q)
                    }
                    SearchFilter.All -> {
                        // In "All", load tracks, top matching artists, and top albums
                        tracksResult = music.search(q)
                        artistsResult = runCatching { DeezerApi.searchArtists(q, limit = 5) }.getOrDefault(emptyList())
                        albumsResult = runCatching { DeezerApi.searchAlbums(q, limit = 6) }.getOrDefault(emptyList())
                    }
                }

                _state.update {
                    it.copy(
                        results = tracksResult,
                        artistResults = artistsResult,
                        albumResults = albumsResult,
                        isLoading = false,
                        searched = true,
                        recent = (listOf(q) + it.recent.filterNot { r -> r.equals(q, ignoreCase = true) }).take(10),
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                val netFail = e is java.io.IOException && e !is app.votify.mobile.data.ApiException
                _state.update {
                    it.copy(
                        isLoading = false,
                        searched = true,
                        offline = netFail,
                        serverMode = music.isServerMode,
                        error = if (netFail) null else (e.message ?: "error"),
                    )
                }
            }
        }
    }
}
