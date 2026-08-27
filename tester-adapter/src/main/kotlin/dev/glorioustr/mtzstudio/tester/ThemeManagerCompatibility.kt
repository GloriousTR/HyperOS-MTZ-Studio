package dev.glorioustr.mtzstudio.tester

object ThemeManagerContract {
    const val RECOMMENDED_VERSION = "2.15.5.46"
    const val PACKAGE_NAME = "com.android.thememanager"

    val SUPPORTED_VERSIONS = setOf("2.15.5.46", "3.0.5.6")

    fun canonicalVersion(versionName: String?): String? = versionName
        ?.trim()
        ?.substringBefore('-')
        ?.takeIf(String::isNotBlank)

    fun behavior(versionName: String?): ThemeManagerBehavior = when (canonicalVersion(versionName)) {
        "2.15.5.46", "3.0.5.6" -> ThemeManagerBehavior.LOCAL_THEME_IMPORT
        "3.0.5.14" -> ThemeManagerBehavior.TEMPORARY_DEFAULT_COMPOSITE
        "3.0.6.8" -> ThemeManagerBehavior.TESTER_ACTIVITY_REMOVED
        else -> ThemeManagerBehavior.UNKNOWN
    }
}

enum class ThemeManagerBehavior(val explanation: String) {
    LOCAL_THEME_IMPORT("Imports the MTZ as an independent local theme"),
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
            ThemeManagerContract.canonicalVersion(versionName) in ThemeManagerContract.SUPPORTED_VERSIONS ||
            behavior == ThemeManagerBehavior.LOCAL_THEME_IMPORT
        )
}
