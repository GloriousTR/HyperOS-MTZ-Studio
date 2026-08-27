package dev.glorioustr.mtzstudio

import android.content.Context
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodDataList

internal data class ThemeProtectionCompatibility(
    val compatible: Boolean,
    val detail: String,
)

internal class ThemeProtectionCompatibilityScanner(private val context: Context) {
    fun scan(): ThemeProtectionCompatibility = runCatching {
        val appInfo = context.packageManager.getApplicationInfo("com.android.thememanager", 0)
        System.loadLibrary("dexkit")
        val (profile, matches) = DexKitBridge.create(appInfo.sourceDir).use { bridge ->
            findThemeRightsCheck(bridge)
        }
        ThemeProtectionCompatibility(
            compatible = matches.size == 1,
            detail = if (matches.size == 1) {
                "$profile · ${matches.single()}"
            } else {
                "$profile · ${matches.size} matching checks"
            },
        )
    }.getOrElse { error ->
        ThemeProtectionCompatibility(
            compatible = false,
            detail = error.message ?: error::class.simpleName.orEmpty(),
        )
    }

    /**
     * Xiaomi changed the surrounding log strings between the legacy 2.15.x branch and
     * current 3.0.x Global builds. Profiles are tried from most specific to least specific,
     * and callers accept the result only when exactly one method remains.
     */
    private fun findThemeRightsCheck(bridge: DexKitBridge): Pair<String, MethodDataList> {
        val legacy = bridge.findMethod(
            FindMethod.create().matcher(
                MethodMatcher.create().usingStrings(
                    "theme",
                    "ThemeManagerTag",
                    "/system",
                    "check rights isLegal: ",
                ),
            ),
        )
        if (legacy.isNotEmpty()) return "legacy-2.15" to legacy

        val currentGlobal = bridge.findMethod(
            FindMethod.create().matcher(
                MethodMatcher.create().usingStrings("check rights"),
            ),
        )
        return "global-3.0" to currentGlobal
    }
}
