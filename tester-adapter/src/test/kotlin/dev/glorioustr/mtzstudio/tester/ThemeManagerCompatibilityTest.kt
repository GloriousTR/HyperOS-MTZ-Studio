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
            ThemeManagerBehavior.MODDED_PERSISTENT_IMPORT,
            ThemeManagerContract.behavior("10.8.7.6"),
        )
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
    fun `modded persistent import disables redundant global protection`() {
        val installed = InstalledThemeManager(
            installed = true,
            packageName = ThemeManagerContract.PACKAGE_NAME,
            versionName = "10.8.7.6",
            versionCode = 10876,
            behavior = ThemeManagerContract.behavior("10.8.7.6"),
        )

        assertTrue(installed.isRecommended)
        assertFalse(installed.requiresGlobalThemeProtection)
    }

    @Test
    fun `verified global path continues to require protection`() {
        val installed = InstalledThemeManager(
            installed = true,
            packageName = ThemeManagerContract.PACKAGE_NAME,
            versionName = "3.0.5.6",
            versionCode = 30506,
            behavior = ThemeManagerContract.behavior("3.0.5.6"),
        )

        assertTrue(installed.isRecommended)
        assertTrue(installed.requiresGlobalThemeProtection)
    }

    @Test
    fun `global tester request stays on the device verified contract`() {
        val request = ThemeManagerContract.legacyTesterRequest(
            themePath = "/staging/Circle-UI-Black-Icons.mtz",
            callerPackage = "dev.glorioustr.mtzstudio",
        )

        assertEquals("com.android.thememanager.support3.0", request.action)
        assertEquals("com.android.thememanager.ApplyThemeForScreenshot", request.componentClassName)
        assertEquals(
            linkedMapOf(
                "theme_file_path" to "/staging/Circle-UI-Black-Icons.mtz",
                "api_called_from" to "dev.glorioustr.mtzstudio",
            ),
            request.stringExtras,
        )
        assertEquals(
            linkedMapOf(
                "theme_apply_flags" to -1L,
                "theme_remove_flags" to -1L,
            ),
            request.longExtras,
        )
        assertEquals(4, request.stringExtras.size + request.longExtras.size)
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
