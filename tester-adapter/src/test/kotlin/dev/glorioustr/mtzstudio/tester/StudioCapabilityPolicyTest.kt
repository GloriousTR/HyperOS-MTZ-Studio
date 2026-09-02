package dev.glorioustr.mtzstudio.tester

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudioCapabilityPolicyTest {
    @Test
    fun `rootless modern devices retain the local editor instead of exposing a private catalog`() {
        val policy = StudioCapabilityPolicy(false, ThemeManagerBehavior.MODERN_NATIVE_LIBRARY)

        assertTrue(policy.usesRootlessWorkspace)
        assertFalse(policy.usesNativeCatalog)
        assertFalse(policy.canReadPrivateThemeManagerData)
        assertFalse(policy.canApplyAutomatically)
    }

    @Test
    fun `rooted modern devices retain the native provider`() {
        val policy = StudioCapabilityPolicy(true, ThemeManagerBehavior.MODERN_NATIVE_LIBRARY)

        assertTrue(policy.usesNativeCatalog)
        assertTrue(policy.canReadPrivateThemeManagerData)
        assertTrue(policy.canApplyAutomatically)
        assertFalse(policy.usesRootlessWorkspace)
    }

    @Test
    fun `global devices use the local workspace with or without root`() {
        assertTrue(StudioCapabilityPolicy(false, ThemeManagerBehavior.LOCAL_THEME_IMPORT).usesRootlessWorkspace)
        assertTrue(StudioCapabilityPolicy(true, ThemeManagerBehavior.LOCAL_THEME_IMPORT).usesRootlessWorkspace)
    }
}
