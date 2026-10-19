package app.votify.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale

class DeezerApiTest {

    @Test
    fun testDeezerTrackToVotifyTrackConversion() {
        val deezerTrack = DeezerTrack(
            id = 3945173111L,
            title = "ЛАБУДА",
            durationSeconds = 796,
            rank = 372454,
            artistName = "NORD DIVISION",
            artistId = 245537042L,
            albumTitle = "ЛАБУДА",
            albumCover = "https://cdn-images.dzcdn.net/images/cover/e453d492938d3059e780ab36c88fff49/1000x1000.jpg",
            previewUrl = "https://cdnt-preview.dzcdn.net/api/1/1/0/9/9/0/preview.mp3",
        )

        val track = deezerTrack.toVotifyTrack()

        assertEquals("dz_3945173111", track.id)
        assertEquals("ЛАБУДА", track.title)
        assertEquals("NORD DIVISION", track.artist)
        assertEquals(796, track.duration)
        assertEquals("https://cdn-images.dzcdn.net/images/cover/e453d492938d3059e780ab36c88fff49/1000x1000.jpg", track.cover)
        assertEquals(372454, track.rank)
    }

    @Test
    fun testTrackMetaCacheStoresAndRetrieves() {
        val meta = DeezerTrackMeta(
            id = 3945173111L,
            title = "ЛАБУДА",
            artist = "NORD DIVISION",
            previewUrl = "https://cdnt-preview.dzcdn.net/preview.mp3",
            duration = 796,
            cover = "https://cdn-images.dzcdn.net/cover.jpg",
        )

        DeezerApi.trackMetaCache["dz_3945173111"] = meta

        val retrieved = DeezerApi.getTrackSync(3945173111L)
        assertNotNull(retrieved)
        assertEquals("ЛАБУДА", retrieved?.title)
        assertEquals("NORD DIVISION", retrieved?.artist)
        assertEquals("https://cdnt-preview.dzcdn.net/preview.mp3", retrieved?.previewUrl)
    }

    @Test
    fun testArtistMonthlyListenersFormatting() {
        val artist = DeezerArtist(
            id = 245537042L,
            name = "NORD DIVISION",
            nbFan = 1640,
            nbAlbum = 36,
        )

        val formattedListeners = NumberFormat.getInstance(Locale.forLanguageTag("ru")).format(artist.nbFan)
        val fullString = "$formattedListeners слушателей в месяц"

        assertTrue(formattedListeners.contains("1") && formattedListeners.contains("640"))
        assertTrue(fullString.contains("слушателей в месяц"))
    }
}
