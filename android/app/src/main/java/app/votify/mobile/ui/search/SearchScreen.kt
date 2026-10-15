package app.votify.mobile.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.votify.mobile.R
import app.votify.mobile.data.DeezerAlbum
import app.votify.mobile.data.DeezerArtist
import app.votify.mobile.data.Track
import app.votify.mobile.ui.components.Artwork
import app.votify.mobile.ui.components.PillChip
import app.votify.mobile.ui.components.SectionHeader
import app.votify.mobile.ui.components.TrackRow
import app.votify.mobile.ui.theme.VotifyColors
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    currentTrackId: String?,
    contentPadding: PaddingValues,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    val searchBarBottom = (contentPadding.calculateBottomPadding() - 24.dp).coerceAtLeast(0.dp) + 6.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Content list: Only shown when searching (query is not blank)
        if (state.query.isNotBlank()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = searchBarBottom + 68.dp,
                ),
            ) {
                // Filter Chips
                item {
                    Spacer(Modifier.height(4.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(SearchFilter.values()) { f ->
                            PillChip(
                                text = f.label,
                                selected = state.filter == f,
                                onClick = { viewModel.setFilter(f) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            // === SEARCH RESULTS STATE ===
            when {
                state.isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VotifyColors.Primary, strokeWidth = 2.dp)
                    }
                }

                state.error != null || state.offline -> item {
                    StatusBlock(
                        title = if (state.offline) stringResource(R.string.search_no_internet_title) else stringResource(R.string.search_error),
                        subtitle = if (state.offline) stringResource(R.string.search_no_internet_sub) else (state.error ?: ""),
                        action = stringResource(R.string.search_retry),
                        onAction = viewModel::retry,
                    )
                }

                state.searched && state.results.isEmpty() && state.artistResults.isEmpty() && state.albumResults.isEmpty() -> item {
                    StatusBlock(
                        title = stringResource(R.string.search_empty),
                        subtitle = stringResource(R.string.search_empty_hint),
                    )
                }

                else -> {
                    // 1. Artist Matches (if filter is All or Artists)
                    if (state.artistResults.isNotEmpty() && (state.filter == SearchFilter.All || state.filter == SearchFilter.Artists)) {
                        item {
                            SectionHeader(
                                title = "Артисты",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(state.artistResults) { artist ->
                                    ArtistSearchResultCard(
                                        artist = artist,
                                        onClick = { onOpenArtist(artist.name) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // 2. Album Matches (if filter is All or Albums)
                    if (state.albumResults.isNotEmpty() && (state.filter == SearchFilter.All || state.filter == SearchFilter.Albums)) {
                        item {
                            SectionHeader(
                                title = "Альбомы",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(state.albumResults) { album ->
                                    AlbumSearchResultCard(
                                        album = album,
                                        onClick = { onOpenArtist(album.artistName) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // 3. Track Results (if filter is All or Tracks)
                    if (state.results.isNotEmpty() && (state.filter == SearchFilter.All || state.filter == SearchFilter.Tracks)) {
                        item {
                            SectionHeader(
                                title = "Треки",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }

                        itemsIndexed(state.results, key = { i, t -> "$i-${t.id}" }) { i, t ->
                            TrackRow(
                                track = t,
                                isCurrent = t.id == currentTrackId,
                                onClick = {
                                    viewModel.addTrackToHistory(t)
                                    onPlay(state.results, i)
                                },
                                onMore = { onMore(t) },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Floating Search Bar at the bottom matching media_1792070867694.jpg
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = searchBarBottom,
            ),
    ) {
        SearchBar(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onSubmit = { keyboard?.hide(); viewModel.submit() },
            onClear = viewModel::clear,
        )
    }
}
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF141416))
            .border(
                width = 0.5.dp,
                color = if (focused) VotifyColors.Primary.copy(alpha = 0.8f) else Color(0xFF27272A),
                shape = RoundedCornerShape(26.dp),
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = if (focused) VotifyColors.Primary else Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp),
        )

        Spacer(Modifier.width(10.dp))

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            cursorBrush = SolidColor(VotifyColors.Primary),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = "Поиск",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VotifyColors.TextMuted,
                    )
                }
                inner()
            },
        )

        if (query.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.search_clear),
                    tint = VotifyColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PopularItemCard(
    item: PopularItem,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF18181B),
        border = BorderStroke(0.5.dp, Color(0xFF27272A)),
        modifier = Modifier.width(220.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                url = item.cover,
                size = 54.dp,
                shape = RoundedCornerShape(10.dp),
                contentDescription = item.title,
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Flat matte badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF141416),
                    modifier = Modifier.padding(bottom = 3.dp),
                ) {
                    Text(
                        text = item.type,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = VotifyColors.Primary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = VotifyColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(
    item: HistoryItem,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF18181B),
        border = BorderStroke(0.5.dp, Color(0xFF27272A)),
        modifier = Modifier.width(180.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.cover.isNotBlank()) {
                Artwork(
                    url = item.cover,
                    size = 38.dp,
                    shape = RoundedCornerShape(8.dp),
                    contentDescription = item.title,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF202024)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = VotifyColors.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { item.query },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = VotifyColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistSearchResultCard(
    artist: DeezerArtist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(105.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color(0xFF18181B))
                .border(0.5.dp, Color(0xFF27272A), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                url = artist.pictureBig.ifBlank { artist.picture },
                size = 90.dp,
                shape = CircleShape,
                contentDescription = artist.name,
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        if (artist.nbFan > 0) {
            Text(
                text = "${NumberFormat.getNumberInstance(Locale.getDefault()).format(artist.nbFan)} фанов",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = VotifyColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AlbumSearchResultCard(
    album: DeezerAlbum,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
    ) {
        Artwork(
            url = album.coverBig.ifBlank { album.cover },
            size = 120.dp,
            shape = RoundedCornerShape(12.dp),
            contentDescription = album.title,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = album.artistName,
            style = MaterialTheme.typography.bodySmall,
            color = VotifyColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusBlock(
    title: String,
    subtitle: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = VotifyColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            PillChip(text = action, selected = true, onClick = onAction)
        }
    }
}
