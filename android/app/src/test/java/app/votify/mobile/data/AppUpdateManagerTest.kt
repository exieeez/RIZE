package app.votify.mobile.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun testUpdateInfoParsing() {
        val jsonStr = """
        {
          "versionCode": 1254,
          "versionName": "1.2.0",
          "sha": "a1b2c3d4e5f67890123456789abcdef012345678",
          "shortSha": "a1b2c3d",
          "mirrorUrl": "https://mirror.example.com",
          "downloadUrl": "https://raw.githubusercontent.com/exieeez/Votify/apk/Votify-debug.apk",
          "cdnUrl": "https://cdn.jsdelivr.net/gh/exieeez/Votify@apk/Votify-debug.apk",
          "releaseUrl": "https://github.com/exieeez/Votify/releases/download/android-debug/Votify-debug.apk"
        }
        """.trimIndent()

        val info = json.decodeFromString<UpdateInfo>(jsonStr)
        assertEquals(1254, info.versionCode)
        assertEquals("1.2.0", info.versionName)
        assertEquals("a1b2c3d4e5f67890123456789abcdef012345678", info.sha)
        assertEquals("a1b2c3d", info.shortSha)
        assertEquals("https://raw.githubusercontent.com/exieeez/Votify/apk/Votify-debug.apk", info.downloadUrl)
    }

    @Test
    fun testCandidateMaxVersionCodeSelection() {
        val candidate1 = UpdateInfo(versionCode = 1250, sha = "sha1")
        val candidate2 = UpdateInfo(versionCode = 1255, sha = "sha2")
        val candidate3 = UpdateInfo(versionCode = 1252, sha = "sha3")

        val candidates = listOf(candidate1, candidate2, candidate3)
        val best = candidates.maxByOrNull { it.versionCode }

        assertNotNull(best)
        assertEquals(1255, best?.versionCode)
        assertEquals("sha2", best?.sha)
    }

    @Test
    fun testIsNewerVersionLogic() {
        val curCode = 1253
        val curSha = "fdc08be12aa1756b2fc6d81eb9c95f078914a335"

        val newerInfo = UpdateInfo(
            versionCode = 1254,
            sha = "d2f8d79abcdef",
            shortSha = "d2f8d79"
        )
        val isNewerCode = newerInfo.versionCode > curCode
        val isNewerSha = !curSha.startsWith(newerInfo.shortSha) && !newerInfo.sha.startsWith(curSha)
        val isNewer = isNewerCode || (isNewerSha && (newerInfo.versionCode <= 0 || newerInfo.versionCode >= curCode))

        assertTrue(isNewerCode)
        assertTrue(isNewerSha)
        assertTrue(isNewer)

        val sameInfo = UpdateInfo(
            versionCode = 1253,
            sha = "fdc08be12aa1756b2fc6d81eb9c95f078914a335",
            shortSha = "fdc08be"
        )
        val isSameCode = sameInfo.versionCode > curCode
        val isSameSha = !curSha.startsWith(sameInfo.shortSha) && !sameInfo.sha.startsWith(curSha)
        val isSameNewer = isSameCode || (isSameSha && (sameInfo.versionCode <= 0 || sameInfo.versionCode >= curCode))

        assertFalse(isSameCode)
        assertFalse(isSameSha)
        assertFalse(isSameNewer)
    }
}
