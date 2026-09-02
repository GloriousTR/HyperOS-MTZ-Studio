package dev.glorioustr.mtzstudio.tester

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * A runtime profile supplements version rules with observable package capabilities.  It never
 * upgrades an unknown build to an automatic import path: a resolved activity alone cannot prove
 * Xiaomi's semantic behaviour.  The profile is instead recorded in diagnostics and paired with a
 * user-exported base APK when a new ROM branch needs a verified compatibility rule.
 */
data class ThemeManagerRuntimeProfile(
    val packageInstalled: Boolean,
    val versionName: String?,
    val knownBehavior: ThemeManagerBehavior,
    val legacyTesterResolvable: Boolean,
    val splitApkCount: Int,
)

class ThemeManagerCapabilityProbe(private val context: Context) {
    fun probe(installed: InstalledThemeManager): ThemeManagerRuntimeProfile {
        if (!installed.installed) {
            return ThemeManagerRuntimeProfile(false, null, ThemeManagerBehavior.UNKNOWN, false, 0)
        }
        val legacyTester = Intent(ThemeManagerContract.LEGACY_TESTER_ACTION).apply {
            component = ComponentName(ThemeManagerContract.PACKAGE_NAME, ThemeManagerContract.LEGACY_TESTER_COMPONENT)
        }
        val resolvable = runCatching {
            context.packageManager.resolveActivity(legacyTester, PackageManager.MATCH_DEFAULT_ONLY) != null
        }.getOrDefault(false)
        val splits = runCatching {
            context.packageManager.getApplicationInfo(ThemeManagerContract.PACKAGE_NAME, 0).splitSourceDirs?.size ?: 0
        }.getOrDefault(0)
        return ThemeManagerRuntimeProfile(
            packageInstalled = true,
            versionName = installed.versionName,
            knownBehavior = installed.behavior,
            legacyTesterResolvable = resolvable,
            splitApkCount = splits,
        )
    }
}
