package dev.glorioustr.mtzstudio.tester

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ThemeManagerCompatibilityTest {
    @Test
    fun `recommended version is recognized with global suffix`() {
        assertEquals("2.15.5.46", ThemeManagerContract.canonicalVersion("2.15.5.46-global"))
        assertEquals(ThemeManagerBehavior.LOCAL_THEME_IMPORT, ThemeManagerContract.behavior("2.15.5.46-global"))
    }

    @Test
    fun `known global versions map to observed tester behavior`() {
        assertEquals(
            ThemeManagerBehavior.TEMPORARY_DEFAULT_COMPOSITE,
            ThemeManagerContract.behavior("3.0.5.14"),
        )
        assertEquals(
            ThemeManagerBehavior.TESTER_ACTIVITY_REMOVED,
            ThemeManagerContract.behavior("3.0.6.8-global"),
        )
        assertEquals(ThemeManagerBehavior.UNKNOWN, ThemeManagerContract.behavior("4.0.0.0"))
    }

    @Test
    fun `root command is limited to replacement and explicit downgrade`() {
        val command = RootInstallCommand.forApkBytes(1234)
        assertEquals("pm install -r -d --user 0 -S 1234 -", command)
        assertFalse("skip-verification" in command)
        assertFalse("uninstall" in command)
    }
}
