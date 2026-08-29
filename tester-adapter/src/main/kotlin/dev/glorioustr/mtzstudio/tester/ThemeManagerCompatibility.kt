package dev.glorioustr.mtzstudio.tester

object ThemeManagerContract {
    const val RECOMMENDED_VERSION = "2.15.5.46"
    const val PACKAGE_NAME = "com.android.thememanager"
    const val LEGACY_TESTER_ACTION = "com.android.thememanager.support3.0"
    const val LEGACY_TESTER_COMPONENT = "com.android.thememanager.ApplyThemeForScreenshot"

    val SUPPORTED_GLOBAL_VERSIONS = setOf("2.15.5.46", "3.0.5.6")
    const val MODDED_PERSISTENT_IMPORT_VERSION = "10.8.7.6"

    fun canonicalVersion(versionName: String?): String? = versionName
        ?.trim()
        ?.substringBefore('-')
        ?.takeIf(String::isNotBlank)

    fun behavior(versionName: String?): ThemeManagerBehavior {
        val canonical = canonicalVersion(versionName) ?: return ThemeManagerBehavior.UNKNOWN
        return when {
            canonical in SUPPORTED_GLOBAL_VERSIONS -> ThemeManagerBehavior.LOCAL_THEME_IMPORT
            canonical == MODDED_PERSISTENT_IMPORT_VERSION -> ThemeManagerBehavior.MODDED_PERSISTENT_IMPORT
            canonical == "3.0.5.14" -> ThemeManagerBehavior.TEMPORARY_DEFAULT_COMPOSITE
            canonical == "3.0.6.8" -> ThemeManagerBehavior.TESTER_ACTIVITY_REMOVED
            else -> ThemeManagerBehavior.UNKNOWN
        }
    }

    /** The exact tester request verified on 2.15.5.46 and 3.0.5.6-global devices. */
    fun legacyTesterRequest(themePath: String, callerPackage: String): LegacyTesterRequest =
        LegacyTesterRequest(
            action = LEGACY_TESTER_ACTION,
            componentClassName = LEGACY_TESTER_COMPONENT,
            stringExtras = linkedMapOf(
                "theme_file_path" to themePath,
                "api_called_from" to callerPackage,
            ),
            longExtras = linkedMapOf(
                "theme_apply_flags" to -1L,
                "theme_remove_flags" to -1L,
            ),
        )
}

data class LegacyTesterRequest(
    val action: String,
    val componentClassName: String,
    val stringExtras: Map<String, String>,
    val longExtras: Map<String, Long>,
)

enum class ThemeManagerBehavior(val explanation: String) {
    LOCAL_THEME_IMPORT("Imports the MTZ as an independent local theme"),
    MODDED_PERSISTENT_IMPORT("The installed module provides persistent third-party MTZ import without Global theme protection"),
    TEMPORARY_DEFAULT_COMPOSITE("Interprets the tester call as a temporary/composite application over Default"),
    TESTER_ACTIVITY_REMOVED("The tester activity is absent"),
    UNKNOWN("Tester behavior is not verified for this version"),
}

data class InstalledThemeManager(
    val installed: Boolean,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long?,
    val behavior: ThemeManagerBehavior,
    internal val signingCertificateSha256: Set<String> = emptySet(),
) {
    val isRecommended: Boolean
        get() = installed && (
            ThemeManagerContract.canonicalVersion(versionName) in ThemeManagerContract.SUPPORTED_GLOBAL_VERSIONS ||
            behavior == ThemeManagerBehavior.LOCAL_THEME_IMPORT ||
            behavior == ThemeManagerBehavior.MODDED_PERSISTENT_IMPORT
        )

    val requiresGlobalThemeProtection: Boolean
        get() = behavior != ThemeManagerBehavior.MODDED_PERSISTENT_IMPORT
}
