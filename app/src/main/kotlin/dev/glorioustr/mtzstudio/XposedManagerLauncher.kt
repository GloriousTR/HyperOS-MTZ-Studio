package dev.glorioustr.mtzstudio

import android.content.Context
import android.content.Intent
import dev.glorioustr.mtzstudio.tester.PrivilegedCommandRunner

internal data class XposedManagerLaunchResult(
    val opened: Boolean,
    val detail: String,
)

/** Opens standalone managers directly and parasitic Vector/LSPosed managers through shell. */
internal class XposedManagerLauncher(
    private val context: Context,
    private val commandRunner: PrivilegedCommandRunner,
) {
    fun open(): XposedManagerLaunchResult {
        openInstalledStandaloneManager()?.let { return it }

        parasiticCategories.forEach { category ->
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(category)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val activities = context.packageManager.queryIntentActivities(intent, 0)
            activities.forEach { resolved ->
                val explicit = Intent(intent).setClassName(
                    resolved.activityInfo.packageName,
                    resolved.activityInfo.name,
                )
                if (runCatching { context.startActivity(explicit) }.isSuccess) {
                    return XposedManagerLaunchResult(true, resolved.activityInfo.packageName)
                }
            }
        }

        installAndOpenBundledVectorManager()?.let { return it }

        val failures = mutableListOf<String>()
        parasiticCategories.forEach { category ->
            val command = buildString {
                append("/system/bin/am start -a android.intent.action.MAIN -c ")
                append(shellQuote(category))
                append(" -n com.android.shell/.BugreportWarningActivity")
            }
            val result = commandRunner.run(command, 30)
            if (result.exitCode == 0 && !result.output.contains("Error:", ignoreCase = true)) {
                return XposedManagerLaunchResult(true, category)
            }
            failures += "$category: ${result.output.takeLast(180).ifBlank { "exit ${result.exitCode}" }}"
        }

        return XposedManagerLaunchResult(
            opened = false,
            detail = failures.joinToString(" · ").ifBlank { "No compatible manager entry point was found" },
        )
    }

    private fun openInstalledStandaloneManager(): XposedManagerLaunchResult? {
        standalonePackages.forEach { packageName ->
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return@forEach
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(launchIntent) }.isSuccess) {
                return XposedManagerLaunchResult(true, packageName)
            }
        }
        return null
    }

    /**
     * Vector ships its official manager APK inside the root module. Some ROMs fail to inject the
     * parasitic manager into com.android.shell after a reboot and show Android's bug-report warning
     * instead. Installing that already-present APK gives the user a stable, normal launcher entry.
     */
    private fun installAndOpenBundledVectorManager(): XposedManagerLaunchResult? {
        val installCommand = buildString {
            append("for apk in")
            bundledVectorManagerApks.forEach { path ->
                append(' ').append(shellQuote(path))
            }
            append("; do if [ -f \"\$apk\" ]; then pm install -r \"\$apk\"; exit \$?; fi; done; exit 2")
        }
        val install = runCatching { commandRunner.run(installCommand, 60) }.getOrNull() ?: return null
        if (install.exitCode != 0 || !install.output.contains("Success", ignoreCase = true)) return null

        openInstalledStandaloneManager()?.let { return it }
        val start = commandRunner.run(
            "/system/bin/am start -n org.matrix.vector.manager/.ui.MainActivity",
            30,
        )
        return if (start.exitCode == 0 && !start.output.contains("Error:", ignoreCase = true)) {
            XposedManagerLaunchResult(true, "org.matrix.vector.manager")
        } else {
            null
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private companion object {
        val standalonePackages = listOf(
            "org.matrix.vector.manager",
            "org.lsposed.manager",
        )
        val bundledVectorManagerApks = listOf(
            "/data/adb/modules/zygisk_vector/manager.apk",
            "/data/adb/modules/vector/manager.apk",
        )
        val parasiticCategories = listOf(
            // Vector and current LSPosed-compatible forks retain this public entry point.
            "org.lsposed.manager.LAUNCH_MANAGER",
            // Compatibility fallback for builds that expose their own Vector category.
            "org.matrix.vector.manager.LAUNCH_MANAGER",
        )
    }
}
