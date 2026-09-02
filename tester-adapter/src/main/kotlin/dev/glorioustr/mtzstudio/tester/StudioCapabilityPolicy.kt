package dev.glorioustr.mtzstudio.tester

/**
 * Keeps the local MTZ editor usable when privileged Theme Manager integration is unavailable.
 * Root changes integration capabilities; it must never gate parsing, composition or export.
 */
data class StudioCapabilityPolicy(
    val rootAvailable: Boolean,
    val themeManagerBehavior: ThemeManagerBehavior,
) {
    val usesNativeCatalog: Boolean
        get() = rootAvailable && themeManagerBehavior == ThemeManagerBehavior.MODERN_NATIVE_LIBRARY

    val canReadPrivateThemeManagerData: Boolean
        get() = rootAvailable

    val canApplyAutomatically: Boolean
        get() = rootAvailable

    val usesRootlessWorkspace: Boolean
        get() = !usesNativeCatalog
}
