package dev.glorioustr.mtzstudio.tester

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `root command stages verified apk in a fixed temporary namespace and cleans it`() {
        val command = RootInstallCommand.forStagedApk(
            "/data/user/0/dev.glorioustr.mtzstudio/cache/theme-manager-update/themes-id.apk",
            "/data/local/tmp/mtzstudio-theme-manager-id.apk",
        )
        assertTrue(command.contains("/system/bin/cp '/data/user/0/dev.glorioustr.mtzstudio/cache/theme-manager-update/themes-id.apk'"))
        assertTrue(command.contains("/system/bin/pm install -r -d --user 0 '/data/local/tmp/mtzstudio-theme-manager-id.apk'"))
        assertTrue(command.contains("/system/bin/rm -f '/data/local/tmp/mtzstudio-theme-manager-id.apk'"))
        assertFalse(" -S " in command)
        assertFalse("skip-verification" in command)
        assertFalse("uninstall" in command)
    }
}
