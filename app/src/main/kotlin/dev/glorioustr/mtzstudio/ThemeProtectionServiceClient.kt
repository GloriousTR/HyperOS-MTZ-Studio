package dev.glorioustr.mtzstudio

import android.content.Context
import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ThemeProtectionState(
    val serviceConnected: Boolean = false,
    val frameworkName: String? = null,
    val frameworkVersion: String? = null,
    val apiVersion: Int? = null,
    val scopesApproved: Boolean = false,
    val systemHookReady: Boolean = false,
    val themeManagerHookReady: Boolean = false,
    val waitingForApproval: Boolean = false,
    val themeManagerCompatible: Boolean? = null,
    val compatibilityDetail: String? = null,
    val error: String? = null,
) {
    val fullyActive: Boolean
        get() = serviceConnected && scopesApproved && systemHookReady && themeManagerHookReady
}

internal object ThemeProtectionServiceClient {
    private const val PREFS_GROUP = "theme_protection"
    private const val KEY_VERSION = "module_version"
    private const val KEY_SYSTEM_READY = "system_hook_ready"
    private const val KEY_THEME_MANAGER_READY = "theme_manager_hook_ready"
    private const val KEY_SYSTEM_ERROR = "system_hook_error"
    private const val KEY_THEME_MANAGER_ERROR = "theme_manager_hook_error"
    private val globalProtectionScopes = listOf("system", "com.android.thememanager")
    private val themeManagerBridgeScope = listOf("com.android.thememanager")
    private val mutableState = MutableStateFlow(ThemeProtectionState())
    val state: StateFlow<ThemeProtectionState> = mutableState.asStateFlow()

    private var initialized = false
    private var protectionRequired = true
    private var service: XposedService? = null
    private var remotePreferences: SharedPreferences? = null
    private var compatibility: ThemeProtectionCompatibility? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    @Synchronized
    fun initialize(context: Context, protectionRequired: Boolean = true) {
        if (initialized) return
        initialized = true
        this.protectionRequired = protectionRequired
        if (protectionRequired) {
            Thread({
                compatibility = ThemeProtectionCompatibilityScanner(context.applicationContext).scan()
                refresh()
            }, "mtz-theme-protection-scan").start()
        }
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(boundService: XposedService) {
                service = boundService
                if (!ThemeProtectionServiceClient.protectionRequired) {
                    // Theme Manager 10.8 only needs its process scope for the apply bridge.
                    // The system-server scope belongs to Global theme protection and is redundant here.
                    // Do not undo a user's scope selection at every application start.
                    // Modern mode requests only Themes; it does not need System Framework.
                    remotePreferences = null
                    mutableState.value = ThemeProtectionState()
                    clientScope.launch {
                        runCatching {
                            dev.glorioustr.mtzstudio.tester.BoundedRemoteCall.await(2_000) {
                                requestThemeManagerBridgeScopeIfNeeded(boundService)
                            }
                        }
                    }
                    return
                }
                runCatching {
                    remotePreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
                    remotePreferences = boundService.getRemotePreferences(PREFS_GROUP).also {
                        it.registerOnSharedPreferenceChangeListener(preferenceListener)
                    }
                }
                refresh()
            }

            override fun onServiceDied(deadService: XposedService) {
                if (service !== deadService) return
                remotePreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
                remotePreferences = null
                service = null
                mutableState.value = ThemeProtectionState(
                    themeManagerCompatible = compatibility?.compatible,
                    compatibilityDetail = compatibility?.detail,
                    error = "Xposed service connection was lost",
                )
            }
        })
    }

    private var commandRunner: ((String) -> String?)? = null

    fun isModernBridgeScopeApproved(): Boolean = runCatching {
        val activeService = service ?: return@runCatching false
        dev.glorioustr.mtzstudio.tester.BoundedRemoteCall.await(2_000) {
            themeManagerBridgeScope.all(activeService.scope.toSet()::contains)
        }
    }.getOrDefault(false)

    fun setCommandRunner(runner: (String) -> String?) {
        commandRunner = runner
    }

    private fun requestThemeManagerBridgeScopeIfNeeded(activeService: XposedService) {
        if (themeManagerBridgeScope.all(activeService.scope.toSet()::contains)) return
        runCatching {
            activeService.requestScope(themeManagerBridgeScope, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) = Unit

                override fun onScopeRequestFailed(message: String) = Unit
            })
        }
    }

    private fun readMarker(fileName: String): Pair<Boolean, String?> {
        val candidates = listOf(
            "/data/system/theme/$fileName",
            "/data/data/com.android.thememanager/files/$fileName",
            "/data/user/0/com.android.thememanager/files/$fileName",
            "/data/local/tmp/$fileName",
        )
        for (path in candidates) {
            val file = java.io.File(path)
            val content = runCatching {
                if (file.exists() && file.canRead()) file.readText() else null
            }.getOrNull() ?: commandRunner?.invoke("cat $path") ?: runCatching {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $path"))
                if (process.waitFor() == 0) {
                    process.inputStream.bufferedReader().use { it.readText() }.trim()
                } else null
            }.getOrNull()

            if (!content.isNullOrBlank() && !content.contains("No such file") && !content.contains("Permission denied")) {
                val lines = content.lines().associate { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
                }
                val ready = lines["ready"]?.toBooleanStrictOrNull() == true
                val error = lines["error"]
                return Pair(ready, error)
            }
        }
        return Pair(false, null)
    }

    private val clientScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    fun refresh() {
        clientScope.launch {
            if (!protectionRequired) {
                mutableState.value = ThemeProtectionState()
                return@launch
            }
            val activeService = service
            if (activeService == null) {
                val (sysReady, _) = readMarker("mtz_protection_system")
                val (tmReady, _) = readMarker("mtz_protection_thememanager")
                mutableState.value = ThemeProtectionState(
                    systemHookReady = sysReady,
                    themeManagerHookReady = tmReady,
                    themeManagerCompatible = compatibility?.compatible,
                    compatibilityDetail = compatibility?.detail,
                )
                return@launch
            }
            runCatching {
                val scopes = activeService.scope.toSet()
                val prefs = remotePreferences ?: runCatching {
                    activeService.getRemotePreferences(PREFS_GROUP).also {
                        remotePreferences = it
                        it.registerOnSharedPreferenceChangeListener(preferenceListener)
                    }
                }.getOrNull()
                val currentMarkers = prefs?.getLong(KEY_VERSION, -1L) == BuildConfig.VERSION_CODE.toLong()
                val (sysReady, sysError) = readMarker("mtz_protection_system")
                val (tmReady, tmError) = readMarker("mtz_protection_thememanager")
                val hookError = listOfNotNull(
                    sysError ?: (if (currentMarkers) prefs?.getString(KEY_SYSTEM_ERROR, null) else null),
                    tmError ?: (if (currentMarkers) prefs?.getString(KEY_THEME_MANAGER_ERROR, null) else null),
                ).joinToString(" · ").ifBlank { null }
                ThemeProtectionState(
                    serviceConnected = true,
                    frameworkName = activeService.frameworkName,
                    frameworkVersion = activeService.frameworkVersion,
                    apiVersion = activeService.apiVersion,
                    scopesApproved = globalProtectionScopes.all(scopes::contains),
                    systemHookReady = sysReady || (currentMarkers && (prefs?.getBoolean(KEY_SYSTEM_READY, false) == true)),
                    themeManagerHookReady = tmReady || (currentMarkers && (prefs?.getBoolean(KEY_THEME_MANAGER_READY, false) == true)),
                    themeManagerCompatible = compatibility?.compatible,
                    compatibilityDetail = compatibility?.detail,
                    error = hookError,
                )
            }.onSuccess { mutableState.value = it }
                .onFailure { error ->
                    mutableState.value = ThemeProtectionState(
                        serviceConnected = true,
                        themeManagerCompatible = compatibility?.compatible,
                        compatibilityDetail = compatibility?.detail,
                        error = error.message ?: error::class.simpleName,
                    )
                }
        }
    }

    fun requestActivation() {
        val activeService = service ?: run {
            mutableState.value = mutableState.value.copy(error = "Xposed service is unavailable")
            return
        }
        mutableState.value = mutableState.value.copy(waitingForApproval = true, error = null)
        runCatching {
            activeService.requestScope(globalProtectionScopes, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    refresh()
                }

                override fun onScopeRequestFailed(message: String) {
                    mutableState.value = mutableState.value.copy(
                        waitingForApproval = false,
                        error = message,
                    )
                }
            })
        }.onFailure { error ->
            mutableState.value = mutableState.value.copy(
                waitingForApproval = false,
                error = error.message ?: error::class.simpleName,
            )
        }
    }

    fun disable() {
        val activeService = service ?: return
        runCatching {
            activeService.removeScope(globalProtectionScopes)
            remotePreferences?.edit()?.clear()?.apply()
        }.onSuccess { refresh() }
            .onFailure { error ->
                mutableState.value = mutableState.value.copy(error = error.message ?: error::class.simpleName)
            }
    }
}
