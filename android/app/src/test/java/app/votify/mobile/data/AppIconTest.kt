package app.votify.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconTest {

    @Test
    fun testAllAppIconsHaveUniqueKeysAndSuffixes() {
        val keys = AppIcon.entries.map { it.key }
        val suffixes = AppIcon.entries.map { it.componentSuffix }

        assertEquals("All keys must be unique", keys.size, keys.toSet().size)
        assertEquals("All component suffixes must be unique", suffixes.size, suffixes.toSet().size)
    }

    @Test
    fun testFromKeyResolvesCorrectly() {
        assertEquals(AppIcon.Current, AppIcon.fromKey("current"))
        assertEquals(AppIcon.Crimson, AppIcon.fromKey("crimson"))
        assertEquals(AppIcon.Mono, AppIcon.fromKey("mono"))
        assertEquals(AppIcon.Mint, AppIcon.fromKey("mint"))
        assertEquals(AppIcon.Lime, AppIcon.fromKey("lime"))
        assertEquals(AppIcon.Violet, AppIcon.fromKey("violet"))
        assertEquals(AppIcon.BlackWhite, AppIcon.fromKey("bw"))
        assertEquals(AppIcon.Glass, AppIcon.fromKey("glass"))
        assertEquals(AppIcon.Classic, AppIcon.fromKey("classic"))
        assertEquals(AppIcon.Custom, AppIcon.fromKey("custom"))

        // Fallback for null or unknown key is Current
        assertEquals(AppIcon.Current, AppIcon.fromKey(null))
        assertEquals(AppIcon.Current, AppIcon.fromKey("unknown_xyz"))
    }

    @Test
    fun testBuiltInListContainsExpectedIcons() {
        val expected = listOf(
            AppIcon.Current,
            AppIcon.Crimson,
            AppIcon.Mono,
            AppIcon.Mint,
            AppIcon.Lime,
            AppIcon.Violet,
            AppIcon.BlackWhite,
            AppIcon.Glass,
            AppIcon.Classic,
        )
        assertEquals(expected, AppIcon.builtIn)
        assertTrue(AppIcon.builtIn.size == 9)
    }
}
