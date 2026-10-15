package app.votify.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DeezerArtist(
    val id: Long,
    val name: String,
    val picture: String = "",
    val pictureBig: String = "",
    val pictureXl: String = "",
    val nbFan: Int = 0,
    val nbAlbum: Int = 0,
)

data class DeezerAlbum(
    val id: Long,
    val title: String,
    val cover: String = "",
    val coverBig: String = "",
    val coverXl: String = "",
    val releaseDate: String = "",
    val recordType: String = "album", // album | single | ep | compile
    val trackCount: Int = 0,
    val artistName: String = "",
    val artistId: Long = 0,
)

data class DeezerTrack(
    val id: Long,
    val title: String,
    val durationSeconds: Int = 0,
    val rank: Int = 0,
    val artistName: String = "",
    val artistId: Long = 0,
    val albumTitle: String = "",
    val albumCover: String = "",
    val previewUrl: String = "",
) {
    fun toVotifyTrack(): Track {
        return Track(
            id = "dz_$id",
            title = title,
            artist = artistName,
            duration = durationSeconds,
            cover = albumCover,
        )
    }
}

/**
 * Public Deezer API client (no developer key required for catalog exploration).
 * Docs: https://developers.deezer.com/api
 */
object DeezerApi {

    private const val BASE_URL = "https://api.deezer.com"

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private suspend fun getJson(path: String, params: Map<String, String> = emptyMap()): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val urlBuilder = "$BASE_URL$path".toHttpUrl().newBuilder()
                for ((k, v) in params) {
                    urlBuilder.addQueryParameter(k, v)
                }
                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", "Votify/1.1.7 (Android)")
                    .get()
                    .build()

                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) null else JSONObject(body)
                }
            }.getOrNull()
        }

    /** Search artists by name. */
    suspend fun searchArtists(query: String, limit: Int = 20): List<DeezerArtist> = withContext(Dispatchers.IO) {
        val json = getJson("/search/artist", mapOf("q" to query, "limit" to limit.toString())) ?: return@withContext emptyList()
        val data = json.optJSONArray("data") ?: return@withContext emptyList()
        parseArtistList(data)
    }

    /** Search albums by name. */
    suspend fun searchAlbums(query: String, limit: Int = 20): List<DeezerAlbum> = withContext(Dispatchers.IO) {
        val json = getJson("/search/album", mapOf("q" to query, "limit" to limit.toString())) ?: return@withContext emptyList()
        val data = json.optJSONArray("data") ?: return@withContext emptyList()
        parseAlbumList(data)
    }

    /** Search tracks by query. */
    suspend fun searchTracks(query: String, limit: Int = 25): List<DeezerTrack> = withContext(Dispatchers.IO) {
        val json = getJson("/search/track", mapOf("q" to query, "limit" to limit.toString())) ?: return@withContext emptyList()
        val data = json.optJSONArray("data") ?: return@withContext emptyList()
        parseTrackList(data)
    }

    /** Get detailed artist profile by ID. */
    suspend fun getArtist(id: Long): DeezerArtist? = withContext(Dispatchers.IO) {
        val json = getJson("/artist/$id") ?: return@withContext null
        if (json.has("error")) return@withContext null
        parseArtist(json)
    }

    /** Find artist by exact or closest name and get full profile. */
    suspend fun getArtistByName(name: String): DeezerArtist? = withContext(Dispatchers.IO) {
        val search = searchArtists(name, limit = 5)
        val best = search.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: search.firstOrNull() ?: return@withContext null
        getArtist(best.id) ?: best
    }

    /** Get artist top tracks. */
    suspend fun getArtistTopTracks(artistId: Long, limit: Int = 30): List<DeezerTrack> = withContext(Dispatchers.IO) {
        val json = getJson("/artist/$artistId/top", mapOf("limit" to limit.toString())) ?: return@withContext emptyList()
        val data = json.optJSONArray("data") ?: return@withContext emptyList()
        parseTrackList(data)
    }

    /** Get artist discography (albums and singles). */
    suspend fun getArtistAlbums(artistId: Long, limit: Int = 50): List<DeezerAlbum> = withContext(Dispatchers.IO) {
        val json = getJson("/artist/$artistId/albums", mapOf("limit" to limit.toString())) ?: return@withContext emptyList()
        val data = json.optJSONArray("data") ?: return@withContext emptyList()
        parseAlbumList(data)
    }

    /** Get tracks for a given album. */
    suspend fun getAlbumTracks(albumId: Long): List<DeezerTrack> = withContext(Dispatchers.IO) {
        val json = getJson("/album/$albumId/tracks", mapOf("limit" to "100")) ?: return@withContext emptyList()
        val data = json.optJSONArray("data") ?: return@withContext emptyList()
        parseTrackList(data)
    }

    /** Get similar / related artists. */
    suspend fun getRelatedArtists(artistId: Long, limit: Int = 20): List<DeezerArtist> = withContext(Dispatchers.IO) {
        val json = getJson("/artist/$artistId/related", mapOf("limit" to limit.toString())) ?: return@withContext emptyList()
        val data = json.optJSONArray("data") ?: return@withContext emptyList()
        parseArtistList(data)
    }

    /** Chart trending tracks. */
    suspend fun getChartTracks(limit: Int = 30): List<DeezerTrack> = withContext(Dispatchers.IO) {
        val json = getJson("/chart/0/tracks", mapOf("limit" to limit.toString())) ?: return@withContext emptyList()
        val data = json.optJSONArray("data") ?: return@withContext emptyList()
        parseTrackList(data)
    }

    /** Chart trending albums. */
    suspend fun getChartAlbums(limit: Int = 30): List<DeezerAlbum> = withContext(Dispatchers.IO) {
        val json = getJson("/chart/0/albums", mapOf("limit" to limit.toString())) ?: return@withContext emptyList()
        val data = json.optJSONArray("data") ?: return@withContext emptyList()
        parseAlbumList(data)
    }

    /** Curated genre top artists for onboarding (e.g. Pop, Rap, Rock, Electronic, Phonk). */
    suspend fun getCuratedArtists(genre: String = "pop", limit: Int = 24): List<DeezerArtist> = withContext(Dispatchers.IO) {
        val seeds = when (genre.lowercase()) {
            "rap", "hip-hop" -> listOf("Drake", "Travis Scott", "Eminem", "Kendrick Lamar", "Kanye West", "Playboi Carti", "Big Baby Tape", "OG Buda", "Kizaru", "Miyagi")
            "rock" -> listOf("Queen", "Nirvana", "Linkin Park", "Rammstein", "Arctic Monkeys", "The Weeknd", "Imagine Dragons", "Король и Шут", "Би-2", "Сплин")
            "phonk" -> listOf("Kordhell", "DVRST", "SXMPRA", "Ghostface Playa", "Pharmacist", "Hensonn", "INTERWORLD", "RAVE", "Pradaaslife", "Kaito Shoma")
            "r&b", "soul" -> listOf("The Weeknd", "SZA", "Frank Ocean", "Brent Faiyaz", "Daniel Caesar", "Joji", "Giveon", "Steve Lacy", "Childish Gambino", "Summer Walker")
            "electronic", "edm" -> listOf("Daft Punk", "Avicii", "David Guetta", "Calvin Harris", "Skrillex", "Marshmello", "Martin Garrix", "Kygo", "Alan Walker", "Swedish House Mafia")
            else -> listOf("Taylor Swift", "Billie Eilish", "Dua Lipa", "Ed Sheeran", "Ariana Grande", "Harry Styles", "Bruno Mars", "Adele", "Rihanna", "Post Malone", "Lady Gaga", "Coldplay")
        }
        val result = mutableListOf<DeezerArtist>()
        for (name in seeds.take(limit)) {
            val a = getArtistByName(name)
            if (a != null && a.picture.isNotBlank()) {
                result.add(a)
            }
        }
        result
    }

    // --- JSON Parsing Helpers ---

    private fun parseArtist(o: JSONObject): DeezerArtist {
        val pic = o.optString("picture_medium").ifBlank { o.optString("picture") }
        val picBig = o.optString("picture_big").ifBlank { pic }
        val picXl = o.optString("picture_xl").ifBlank { picBig }
        return DeezerArtist(
            id = o.optLong("id"),
            name = o.optString("name"),
            picture = pic,
            pictureBig = picBig,
            pictureXl = picXl,
            nbFan = o.optInt("nb_fan"),
            nbAlbum = o.optInt("nb_album"),
        )
    }

    private fun parseArtistList(arr: JSONArray): List<DeezerArtist> {
        val list = mutableListOf<DeezerArtist>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(parseArtist(o))
        }
        return list
    }

    private fun parseAlbum(o: JSONObject): DeezerAlbum {
        val cover = o.optString("cover_medium").ifBlank { o.optString("cover") }
        val coverBig = o.optString("cover_big").ifBlank { cover }
        val coverXl = o.optString("cover_xl").ifBlank { coverBig }
        val artistObj = o.optJSONObject("artist")
        return DeezerAlbum(
            id = o.optLong("id"),
            title = o.optString("title"),
            cover = cover,
            coverBig = coverBig,
            coverXl = coverXl,
            releaseDate = o.optString("release_date"),
            recordType = o.optString("record_type", "album"),
            trackCount = o.optInt("nb_tracks"),
            artistName = artistObj?.optString("name").orEmpty(),
            artistId = artistObj?.optLong("id") ?: 0L,
        )
    }

    private fun parseAlbumList(arr: JSONArray): List<DeezerAlbum> {
        val list = mutableListOf<DeezerAlbum>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(parseAlbum(o))
        }
        return list
    }

    private fun parseTrack(o: JSONObject): DeezerTrack {
        val artistObj = o.optJSONObject("artist")
        val albumObj = o.optJSONObject("album")
        val cover = albumObj?.optString("cover_medium") ?: albumObj?.optString("cover").orEmpty()
        val coverBig = albumObj?.optString("cover_big") ?: cover
        val coverXl = albumObj?.optString("cover_xl") ?: coverBig
        return DeezerTrack(
            id = o.optLong("id"),
            title = o.optString("title"),
            durationSeconds = o.optInt("duration"),
            rank = o.optInt("rank"),
            artistName = artistObj?.optString("name").orEmpty(),
            artistId = artistObj?.optLong("id") ?: 0L,
            albumTitle = albumObj?.optString("title").orEmpty(),
            albumCover = coverXl.ifBlank { coverBig.ifBlank { cover } },
            previewUrl = o.optString("preview"),
        )
    }

    private fun parseTrackList(arr: JSONArray): List<DeezerTrack> {
        val list = mutableListOf<DeezerTrack>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(parseTrack(o))
        }
        return list
    }
}
