package app.votify.mobile.data

import app.votify.mobile.data.local.FavoriteEntity
import app.votify.mobile.data.local.HistoryEntity
import app.votify.mobile.data.local.PlaylistEntity
import app.votify.mobile.data.local.PlaylistTrackEntity
import app.votify.mobile.data.local.TrackEntity
import app.votify.mobile.data.local.VotifyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Compact wire format for one track inside the sync blob. */
@Serializable
data class SyncTrack(val id: String, val t: String, val a: String, val c: String, val d: Int)

@Serializable
data class SyncFavorite(val addedAt: Long, val track: SyncTrack)

@Serializable
data class SyncPlaylist(val name: String, val createdAt: Long, val tracks: List<SyncTrack>, val cover: String? = null)

@Serializable
data class SyncHistoryItem(val playedAt: Long, val track: SyncTrack)

/** Everything that travels to the account cloud: settings + favorites + playlists + history. */
@Serializable
data class SyncBlob(
    val v: Int = 1,
    val theme: String = "",
    val customTheme: String = "",
    val customPrefs: String = "",
    val backgroundUrl: String = "",
    val favorites: List<SyncFavorite> = emptyList(),
    val playlists: List<SyncPlaylist> = emptyList(),
    val history: List<SyncHistoryItem> = emptyList(),
)

/**
 * Account cloud sync for standalone (Firebase) mode: favorites, playlists, history and settings
 * are synchronized in real time with the user's private Firestore document and PC app.
 */
class CloudSync(
    private val db: VotifyDatabase,
    private val settingsRepo: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** True when the local library holds anything worth keeping (favorites / playlists / history). */
    suspend fun hasLocalData(): Boolean =
        db.favorites().count() > 0 || db.playlists().count() > 0

    suspend fun exportBlob(): SyncBlob {
        val s = settingsRepo.settings.first()
        val tracks = db.tracks().syncExport().associateBy { it.id }
        val favorites = db.favorites().getAll().mapNotNull { f ->
            tracks[f.trackId]?.let { SyncFavorite(f.addedAt, SyncTrack(it.id, it.title, it.artist, it.cover, it.duration)) }
        }
        val playlists = db.playlists().getAll().map { p ->
            SyncPlaylist(
                name = p.name,
                createdAt = p.createdAt,
                tracks = db.playlists().tracksOf(p.id).map { SyncTrack(it.id, it.title, it.artist, it.cover, it.duration) },
                cover = p.customCover,
            )
        }
        val historyEntries = db.history().getRecent(100)
        val history = historyEntries.mapNotNull { h ->
            tracks[h.trackId]?.let { SyncHistoryItem(h.playedAt, SyncTrack(it.id, it.title, it.artist, it.cover, it.duration)) }
        }
        return SyncBlob(
            theme = s.theme.key,
            customTheme = s.customTheme,
            customPrefs = s.customPrefs,
            backgroundUrl = s.backgroundUrl,
            favorites = favorites,
            playlists = playlists,
            history = history,
        )
    }

    suspend fun importBlob(blob: SyncBlob) {
        if (blob.theme.isNotBlank()) settingsRepo.setTheme(AppTheme.fromKey(blob.theme))
        settingsRepo.setBackgroundUrl(blob.backgroundUrl)
        if (blob.customTheme.isNotBlank()) settingsRepo.setCustomTheme(blob.customTheme)
        if (blob.customPrefs.isNotBlank()) settingsRepo.setCustomPrefs(blob.customPrefs)

        // Favorites: upsert the tracks, insert links, delete local favorites not in cloud
        val favTracks = blob.favorites.map { TrackEntity(it.track.id, it.track.t, it.track.a, it.track.c, it.track.d) }
        if (favTracks.isNotEmpty()) {
            db.tracks().upsertAll(favTracks)
            db.favorites().insertAll(blob.favorites.map { FavoriteEntity(it.track.id, it.addedAt) })
        }
        val cloudFavIds = blob.favorites.map { it.track.id }.toSet()
        val localFavs = db.favorites().getAll()
        localFavs.forEach { f ->
            if (f.trackId !in cloudFavIds) {
                db.favorites().delete(f.trackId)
            }
        }

        // Playlists: reconcile existing by name or insert new, sync tracks without creating duplicate playlists!
        val now = System.currentTimeMillis()
        val cloudPlNames = blob.playlists.map { it.name }.toSet()
        val localPlaylists = db.playlists().getAll()
        localPlaylists.forEach { lp ->
            if (lp.name !in cloudPlNames) {
                db.playlists().clearTracks(lp.id)
                db.playlists().delete(lp.id)
            }
        }
        blob.playlists.forEach { p ->
            val existing = db.playlists().byName(p.name)
            val pid = existing?.id ?: db.playlists().insert(PlaylistEntity(0, p.name, p.createdAt, now, customCover = p.cover))
            if (existing != null && p.cover != null) {
                db.playlists().updateCover(pid, p.cover, now)
            }
            db.playlists().clearTracks(pid)
            p.tracks.forEachIndexed { i, t ->
                db.tracks().upsert(TrackEntity(t.id, t.t, t.a, t.c, t.d))
                db.playlists().insertTrack(PlaylistTrackEntity(pid, t.id, i, now))
            }
        }

        // History: upsert tracks, insert history entries
        val histTracks = blob.history.map { TrackEntity(it.track.id, it.track.t, it.track.a, it.track.c, it.track.d) }
        if (histTracks.isNotEmpty()) {
            db.tracks().upsertAll(histTracks)
            val entries = blob.history.map { HistoryEntity(id = 0, trackId = it.track.id, playedAt = it.playedAt) }
            db.history().insertAll(entries)
            db.history().trim(keep = 2000)
        }
    }

    fun toJson(blob: SyncBlob): String = json.encodeToString(SyncBlob.serializer(), blob)

    fun fromJson(raw: String): SyncBlob? =
        runCatching { json.decodeFromString<SyncBlob>(raw) }.getOrNull()
}
