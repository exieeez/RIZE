package app.votify.mobile.ui.artist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.votify.mobile.R
import app.votify.mobile.data.DeezerAlbum
import app.votify.mobile.data.DeezerApi
import app.votify.mobile.data.DeezerArtist
import app.votify.mobile.data.DeezerTrack
import app.votify.mobile.data.MusicRepository
import app.votify.mobile.data.Track
import app.votify.mobile.ui.components.Artwork
import app.votify.mobile.ui.components.CircleIconButton
import app.votify.mobile.ui.components.GlassBaseColor
import app.votify.mobile.ui.components.LocalLiquidGlass
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.SectionHeader
import app.votify.mobile.ui.components.TrackRow
import app.votify.mobile.ui.components.formatDuration
import app.votify.mobile.ui.components.pluralTracks
import app.votify.mobile.ui.components.rememberGlassSpec
import app.votify.mobile.ui.components.votifyGlass
import app.votify.mobile.ui.library.EmptyCollection
import app.votify.mobile.ui.theme.VotifyColors
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

data class ArtistUiState(
    val name: String = "",
    val artist: DeezerArtist? = null,
    val tracks: List<Track> = emptyList(),
    val albums: List<DeezerAlbum> = emptyList(),
    val singles: List<DeezerAlbum> = emptyList(),
    val relatedArtists: List<DeezerArtist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedAlbum: DeezerAlbum? = null,
    val albumTracks: List<DeezerTrack> = emptyList(),
    val isLoadingAlbumTracks: Boolean = false,
    val isFollowing: Boolean = false,
)

/**
 * Artist profile data powered by Deezer API (with graceful NewPipe fallback).
 */
class ArtistViewModel(private val music: MusicRepository) : ViewModel() {

    private val _state = MutableStateFlow(ArtistUiState())
    val state: StateFlow<ArtistUiState> = _state
    private var job: Job? = null
    private var albumJob: Job? = null

    fun load(name: String, force: Boolean = false) {
        if (!force && name == _state.value.name && (_state.value.tracks.isNotEmpty() || _state.value.isLoading)) return
        job?.cancel()
        _state.value = ArtistUiState(name = name, isLoading = true)

        job = viewModelScope.launch {
            try {
                // 1. Fetch artist details from Deezer API
                val deezerArtist = DeezerApi.getArtistByName(name)
                if (deezerArtist != null) {
                    val topDeezerTracks = DeezerApi.getArtistTopTracks(deezerArtist.id, limit = 40)
                    val topTracks = topDeezerTracks.map { it.toVotifyTrack() }.ifEmpty {
                        music.artist(name, limit = 40)
                    }

                    val allAlbums = DeezerApi.getArtistAlbums(deezerArtist.id, limit = 60)
                    val studioAlbums = allAlbums.filter { it.recordType.equals("album", ignoreCase = true) }
                    val singlesAndEps = allAlbums.filter { !it.recordType.equals("album", ignoreCase = true) }

                    val related = DeezerApi.getRelatedArtists(deezerArtist.id, limit = 16)

                    _state.update {
                        it.copy(
                            artist = deezerArtist,
                            tracks = topTracks,
                            albums = studioAlbums.ifEmpty { allAlbums },
                            singles = singlesAndEps,
                            relatedArtists = related,
                            isLoading = false,
                            error = null,
                        )
                    }
                } else {
                    // Fallback to internal search
                    val tracks = music.artist(name, limit = 50)
                    _state.update { it.copy(tracks = tracks, isLoading = false, error = null) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                // Secondary fallback on connection issues
                val tracks = runCatching { music.artist(name, limit = 50) }.getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        tracks = tracks,
                        isLoading = false,
                        error = if (tracks.isEmpty()) (e.message ?: "error") else null,
                    )
                }
            }
        }
    }

    fun openAlbum(album: DeezerAlbum) {
        albumJob?.cancel()
        _state.update { it.copy(selectedAlbum = album, albumTracks = emptyList(), isLoadingAlbumTracks = true) }
        albumJob = viewModelScope.launch {
            val tracks = DeezerApi.getAlbumTracks(album.id)
            _state.update { it.copy(albumTracks = tracks, isLoadingAlbumTracks = false) }
        }
    }

    fun closeAlbum() {
        albumJob?.cancel()
        _state.update { it.copy(selectedAlbum = null, albumTracks = emptyList(), isLoadingAlbumTracks = false) }
    }

    fun toggleFollow() {
        _state.update { it.copy(isFollowing = !it.isFollowing) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    viewModel: ArtistViewModel,
    name: String,
    currentTrackId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
    onOpenArtist: (String) -> Unit = {},
) {
    LaunchedEffect(name) { viewModel.load(name) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Обзор", "Альбомы", "Синглы и EP", "Похожие")

    val isGlass = LocalLiquidGlass.current
    val spec = rememberGlassSpec()

    // Album tracklist bottom sheet (1-to-1 design matching screenshot)
    state.selectedAlbum?.let { album ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closeAlbum,
            sheetState = sheetState,
            containerColor = Color.Black,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            dragHandle = null,
        ) {
            AlbumTracksSheetContent(
                album = album,
                tracks = state.albumTracks,
                isLoading = state.isLoadingAlbumTracks,
                currentTrackId = currentTrackId,
                onPlayTrack = { index ->
                    val votifyTracks = state.albumTracks.map { it.toVotifyTrack() }
                    if (votifyTracks.isNotEmpty()) onPlay(votifyTracks, index)
                },
                onPlayAll = {
                    val votifyTracks = state.albumTracks.map { it.toVotifyTrack() }
                    if (votifyTracks.isNotEmpty()) onPlay(votifyTracks, 0)
                },
                onShuffleAll = {
                    val votifyTracks = state.albumTracks.map { it.toVotifyTrack() }
                    if (votifyTracks.isNotEmpty()) onPlay(votifyTracks.shuffled(), 0)
                },
                onClose = viewModel::closeAlbum,
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentPadding = PaddingValues(
            top = 0.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        // --- 1. Immersive Artist Header ---
        item {
            ArtistHeader(
                name = name,
                artist = state.artist,
                tracksCount = state.tracks.size,
                isFollowing = state.isFollowing,
                contentTopPadding = contentPadding.calculateTopPadding(),
                onBack = onBack,
                onToggleFollow = viewModel::toggleFollow,
                onPlayTop = { if (state.tracks.isNotEmpty()) onPlay(state.tracks, 0) },
                onShuffleTop = {
                    if (state.tracks.isNotEmpty()) {
                        val shuffled = state.tracks.shuffled()
                        onPlay(shuffled, 0)
                    }
                },
            )
        }

        // --- 2. Filter Navigation Tabs ---
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = VotifyColors.TextPrimary,
                edgePadding = 16.dp,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTab in tabPositions.indices) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = VotifyColors.Primary,
                            height = 3.dp,
                        )
                    }
                },
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Color.White else VotifyColors.TextSecondary,
                            )
                        },
                    )
                }
            }
        }

        // --- 3. Body Content based on State and Tab ---
        when {
            state.isLoading -> item {
                Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VotifyColors.Primary, strokeWidth = 2.dp)
                }
            }

            state.error != null && state.tracks.isEmpty() -> item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.search_error), color = VotifyColors.TextSecondary, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(state.error ?: "", color = VotifyColors.TextMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    PillChip(text = stringResource(R.string.search_retry), selected = true, onClick = { viewModel.load(name, force = true) })
                }
            }

            else -> {
                when (selectedTab) {
                    0 -> {
                        // --- Overview Tab ---
                        // Top tracks
                        item {
                            SectionHeader(
                                title = "Популярные треки",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }

                        val previewTracks = state.tracks.take(20)
                        if (previewTracks.isEmpty()) {
                            item { EmptyCollection(stringResource(R.string.artist_empty)) }
                        } else {
                            itemsIndexed(previewTracks, key = { i, t -> "$i-${t.id}" }) { i, t ->
                                ArtistTrackRow(
                                    index = i,
                                    track = t,
                                    isCurrent = t.id == currentTrackId,
                                    onClick = { onPlay(state.tracks, i) },
                                )
                            }
                        }

                        // Studio Albums Highlight
                        if (state.albums.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(16.dp))
                                SectionHeader(
                                    title = "Релизы",
                                    action = "Все (${state.albums.size})",
                                    onAction = { selectedTab = 1 },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(state.albums.take(8)) { album ->
                                        AlbumItemCard(album = album, onClick = { viewModel.openAlbum(album) })
                                    }
                                }
                            }
                        }

                        // Similar Artists Highlight
                        if (state.relatedArtists.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(20.dp))
                                SectionHeader(
                                    title = "Похожие исполнители",
                                    action = "Все (${state.relatedArtists.size})",
                                    onAction = { selectedTab = 3 },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(state.relatedArtists.take(8)) { artist ->
                                        SimilarArtistItemCard(
                                            artist = artist,
                                            onClick = {
                                                onOpenArtist(artist.name)
                                                viewModel.load(artist.name)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // --- Albums Tab ---
                        if (state.albums.isEmpty()) {
                            item { EmptyCollection("Альбомы не найдены") }
                        } else {
                            item {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.height(550.dp),
                                ) {
                                    items(state.albums) { album ->
                                        AlbumItemCard(album = album, onClick = { viewModel.openAlbum(album) })
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // --- Singles & EPs Tab ---
                        if (state.singles.isEmpty()) {
                            item { EmptyCollection("Синглы и EP не найдены") }
                        } else {
                            item {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.height(550.dp),
                                ) {
                                    items(state.singles) { single ->
                                        AlbumItemCard(album = single, onClick = { viewModel.openAlbum(single) })
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // --- Similar Artists Tab ---
                        if (state.relatedArtists.isEmpty()) {
                            item { EmptyCollection("Похожие исполнители не найдены") }
                        } else {
                            item {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                    modifier = Modifier.height(550.dp),
                                ) {
                                    items(state.relatedArtists) { artist ->
                                        SimilarArtistItemCard(
                                            artist = artist,
                                            onClick = {
                                                onOpenArtist(artist.name)
                                                viewModel.load(artist.name)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Artist header matching screenshot 1-to-1:
 * - Liquid Glass < and ··· top circular buttons
 * - Hero banner fading downwards into solid black
 * - Left-aligned bold white title + avatar/artist subtitle row
 * - Action buttons row: [ ▶ Play ] white pill, circular glass Shuffle, circular glass Add
 */
@Composable
private fun ArtistHeader(
    name: String,
    artist: DeezerArtist?,
    tracksCount: Int,
    isFollowing: Boolean,
    contentTopPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onToggleFollow: () -> Unit,
    onPlayTop: () -> Unit,
    onShuffleTop: () -> Unit,
) {
    val photoUrl = artist?.pictureXl ?: artist?.pictureBig ?: artist?.picture.orEmpty()
    val isGlass = LocalLiquidGlass.current
    val spec = rememberGlassSpec()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black),
    ) {
        // Hero Artwork Area: large edge-to-edge square reaching the screen boundaries
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            if (photoUrl.isNotBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF141416)),
                )
            }

            // Smooth vertical gradient to solid black
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.35f),
                                0.35f to Color.Transparent,
                                0.70f to Color.Black.copy(alpha = 0.65f),
                                1.0f to Color.Black,
                            )
                        )
                    )
            )

            // Top bar buttons overlay: Left <, Right ··· (Liquid Glass)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .size(44.dp)
                        .votifyGlass(
                            shape = CircleShape,
                            baseColor = if (isGlass) GlassBaseColor else Color.Black.copy(alpha = 0.35f),
                            spec = spec,
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Surface(
                    onClick = onToggleFollow,
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .size(44.dp)
                        .votifyGlass(
                            shape = CircleShape,
                            baseColor = if (isGlass) GlassBaseColor else Color.Black.copy(alpha = 0.35f),
                            spec = spec,
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            contentDescription = "Опции",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }

        // Header Content on Pure Solid Black Background
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            // Big Bold Title (left-aligned)
            Text(
                text = artist?.name ?: name,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(10.dp))

            // Subtitle Row: Mini avatar + Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = artist?.name ?: name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(18.dp))

            // Action Buttons Row: [ ▶ Play ] pill, Circular 🔀, Circular +
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Wide White Play Pill Button
                Surface(
                    onClick = onPlayTop,
                    shape = CircleShape,
                    color = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Play",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                        )
                    }
                }

                // Circular Glass Shuffle Button
                Surface(
                    onClick = onShuffleTop,
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .size(48.dp)
                        .votifyGlass(
                            shape = CircleShape,
                            baseColor = if (isGlass) GlassBaseColor else Color(0xFF222226),
                            spec = spec,
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Shuffle,
                            contentDescription = "Перемешать",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // Circular Glass Add / Follow Button
                Surface(
                    onClick = onToggleFollow,
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .size(48.dp)
                        .votifyGlass(
                            shape = CircleShape,
                            baseColor = if (isGlass) GlassBaseColor else Color(0xFF222226),
                            spec = spec,
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isFollowing) Icons.Filled.Favorite else Icons.Outlined.Add,
                            contentDescription = "Добавить",
                            tint = if (isFollowing) VotifyColors.Primary else Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Minimal numbered track row matching screenshot 1-to-1:
 * - Number on left
 * - Bold title + artist subtitle in middle
 * - Duration capsule [ 2:46 ] on right
 */
@Composable
private fun ArtistTrackRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = if (isCurrent) VotifyColors.Primary else Color(0xFF8E8E93),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(28.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) VotifyColors.Primary else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Color(0xFF8E8E93),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1C1C1E).copy(alpha = 0.85f),
        ) {
            Text(
                text = formatDuration(track.duration * 1000L),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = Color(0xFF8E8E93),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AlbumItemCard(
    album: DeezerAlbum,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        val coverUrl = album.coverXl.ifBlank { album.coverBig.ifBlank { album.cover } }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E24))
                .border(0.5.dp, VotifyColors.BorderSubtle, RoundedCornerShape(14.dp)),
        ) {
            Artwork(
                url = coverUrl,
                size = 140.dp,
                shape = RoundedCornerShape(14.dp),
                contentDescription = album.title,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = VotifyColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val year = album.releaseDate.take(4)
        val sub = if (year.isNotBlank()) year else album.recordType.replaceFirstChar { it.uppercase() }
        Text(
            text = sub,
            style = MaterialTheme.typography.bodySmall,
            color = VotifyColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SimilarArtistItemCard(
    artist: DeezerArtist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val photo = artist.pictureBig.ifBlank { artist.pictureXl.ifBlank { artist.picture } }
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Color(0xFF18181E))
                .border(0.5.dp, VotifyColors.BorderSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                url = photo,
                size = 110.dp,
                shape = CircleShape,
                contentDescription = artist.name,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = VotifyColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (artist.nbFan > 0) {
            Text(
                text = "${NumberFormat.getNumberInstance(Locale.getDefault()).format(artist.nbFan)} фанов",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = VotifyColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AlbumTracksSheetContent(
    album: DeezerAlbum,
    tracks: List<DeezerTrack>,
    isLoading: Boolean,
    currentTrackId: String?,
    onPlayTrack: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onClose: () -> Unit,
) {
    val isGlass = LocalLiquidGlass.current
    val spec = rememberGlassSpec()
    val coverUrl = album.coverXl.ifBlank { album.coverBig.ifBlank { album.cover } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black),
    ) {
        // Hero Artwork Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        ) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF141416)))
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.35f),
                                0.35f to Color.Transparent,
                                0.70f to Color.Black.copy(alpha = 0.65f),
                                1.0f to Color.Black,
                            )
                        )
                    )
            )

            // Top bar buttons overlay: Left <, Right ··· (Liquid Glass)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .size(44.dp)
                        .votifyGlass(
                            shape = CircleShape,
                            baseColor = if (isGlass) GlassBaseColor else Color.Black.copy(alpha = 0.35f),
                            spec = spec,
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Surface(
                    onClick = { /* options */ },
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .size(44.dp)
                        .votifyGlass(
                            shape = CircleShape,
                            baseColor = if (isGlass) GlassBaseColor else Color.Black.copy(alpha = 0.35f),
                            spec = spec,
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            contentDescription = "Опции",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }

        // Header Content on Solid Black
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = album.artistName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Action Buttons Row: [ ▶ Play ] pill, Circular 🔀, Circular +
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    onClick = onPlayAll,
                    shape = CircleShape,
                    color = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Play",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                        )
                    }
                }

                Surface(
                    onClick = onShuffleAll,
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .size(48.dp)
                        .votifyGlass(
                            shape = CircleShape,
                            baseColor = if (isGlass) GlassBaseColor else Color(0xFF222226),
                            spec = spec,
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Shuffle, contentDescription = "Перемешать", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }

                Surface(
                    onClick = { /* Add to library */ },
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .size(48.dp)
                        .votifyGlass(
                            shape = CircleShape,
                            baseColor = if (isGlass) GlassBaseColor else Color(0xFF222226),
                            spec = spec,
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Add, contentDescription = "Добавить", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Numbered track list
        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VotifyColors.Primary, strokeWidth = 2.dp)
            }
        } else if (tracks.isEmpty()) {
            EmptyCollection("Треклист недоступен")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                itemsIndexed(tracks) { index, t ->
                    val isCurrent = currentTrackId == "dz_${t.id}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(index) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            color = if (isCurrent) VotifyColors.Primary else Color(0xFF8E8E93),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = t.title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCurrent) VotifyColors.Primary else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = t.artistName,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = Color(0xFF8E8E93),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1C1C1E).copy(alpha = 0.85f),
                        ) {
                            Text(
                                text = formatDuration(t.durationSeconds * 1000L),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                color = Color(0xFF8E8E93),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
