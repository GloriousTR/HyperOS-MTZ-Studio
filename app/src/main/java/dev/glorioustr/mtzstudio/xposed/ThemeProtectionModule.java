package dev.glorioustr.mtzstudio.xposed;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.glorioustr.mtzstudio.BuildConfig;
import dev.glorioustr.mtzstudio.ThemeManagerBridgeContract;
import io.github.libxposed.api.XposedModule;

/**
 * Narrow Xposed integration that keeps locally imported themes from being rejected later.
 * The module deliberately scopes itself only to system_server and Xiaomi Theme Manager.
 */
public final class ThemeProtectionModule extends XposedModule {
    private static final String TAG = "MTZStudioProtection";
    private static final String TARGET_THEME_MANAGER = "com.android.thememanager";
    private static final String PREFS_GROUP = "theme_protection";
    private static final String KEY_VERSION = "module_version";
    private static final String KEY_SYSTEM_READY = "system_hook_ready";
    private static final String KEY_THEME_MANAGER_READY = "theme_manager_hook_ready";
    private static final String KEY_SYSTEM_ERROR = "system_hook_error";
    private static final String KEY_THEME_MANAGER_ERROR = "theme_manager_hook_error";
    private static final ThreadLocal<Boolean> THEME_VALIDATION_IN_PROGRESS = new ThreadLocal<>();
    private static final String IMPORT_COMPLETE = "action_resource_import_complete";
    private static final String IMPORT_FAILED = "action_resource_import_fail";
    private static final String EXTRA_IMPORTED_RESOURCE = "extra_resource";
    private static final long IMPORT_APPLY_TIMEOUT_MS = 180_000L;

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Loaded in " + param.getProcessName());
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        writeMarkerFile("mtz_protection_system", false, null);
        try {
            installSystemValidationHooks(param.getClassLoader());
            writeMarkerFile("mtz_protection_system", true, null);
            log(Log.INFO, TAG, "System theme validation protection is ready");
        } catch (Throwable error) {
            writeMarkerFile("mtz_protection_system", false, concise(error));
            log(Log.ERROR, TAG, "System validation hook was not installed", error);
        }
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.isFirstPackage() || !TARGET_THEME_MANAGER.equals(param.getPackageName())) return;
        writeMarkerFile("mtz_protection_thememanager", false, null);
        writeMarkerFile(ThemeManagerBridgeContract.BRIDGE_MARKER, false, "bridge not initialized");
        try {
            if (hasThemeManager10_8ImportSurface(param.getClassLoader())) {
                installThemeManagerImportBridge(param.getClassLoader());
                writeMarkerFile(ThemeManagerBridgeContract.BRIDGE_MARKER, true, null);
                log(Log.INFO, TAG, "Theme Manager 10.8 bridge is ready; Global rights protection is not required");
            } else {
                installThemeManagerRightsHook(
                    param.getApplicationInfo().sourceDir,
                    param.getClassLoader()
                );
                writeMarkerFile("mtz_protection_thememanager", true, null);
                log(Log.INFO, TAG, "Theme Manager rights protection is ready");
            }
        } catch (Throwable error) {
            writeMarkerFile("mtz_protection_thememanager", false, concise(error));
            writeMarkerFile(ThemeManagerBridgeContract.BRIDGE_MARKER, false, concise(error));
            log(Log.ERROR, TAG, "Theme Manager rights hook was not installed", error);
        }
    }

    private static boolean hasThemeManager10_8ImportSurface(ClassLoader classLoader) {
        try {
            Class.forName(
                "com.android.thememanager.mine.remote.view.activity.MineResourceTabActivity",
                false,
                classLoader
            );
            Class.forName(
                "com.android.thememanager.mine.local.resource.ThemeImportHandler",
                false,
                classLoader
            );
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    /**
     * Theme Manager 10.8 removed Xiaomi's exported tester activity. Its module-provided local
     * importer remains authoritative, so this bridge validates one MTZ Studio request, imports
     * through that native path, and applies the resulting local Resource.
     */
    private void installThemeManagerImportBridge(ClassLoader classLoader) throws Exception {
        Class<?> localThemeActivity = Class.forName(
            "com.android.thememanager.mine.remote.view.activity.MineResourceTabActivity",
            false,
            classLoader
        );
        Method onCreate = localThemeActivity.getDeclaredMethod("onCreate", Bundle.class);
        onCreate.setAccessible(true);
        hook(onCreate)
            .setPriority(PRIORITY_HIGHEST)
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                Object result = chain.proceed();
                Activity activity = (Activity) chain.getThisObject();
                Intent request = activity.getIntent();
                if (request != null && ThemeManagerBridgeContract.ACTION_APPLY_10_8.equals(request.getAction())) {
                    activity.runOnUiThread(() -> beginThemeManager10_8Request(activity, classLoader, request));
                }
                return result;
            });
    }

    private void beginThemeManager10_8Request(Activity activity, ClassLoader classLoader, Intent request) {
        try {
            String path = request.getStringExtra(ThemeManagerBridgeContract.EXTRA_THEME_PATH);
            String expectedHash = request.getStringExtra(ThemeManagerBridgeContract.EXTRA_THEME_SHA256);
            File themeFile = validateBridgeThemeFile(activity, path, expectedHash);
            new BridgeSession(activity, classLoader, themeFile, expectedHash).start();
        } catch (Throwable error) {
            finishBridgeActivity(activity, false, concise(error));
            log(Log.ERROR, TAG, "Theme Manager 10.8 request rejected", error);
        }
    }

    private File validateBridgeThemeFile(Activity activity, String path, String expectedHash) throws Exception {
        if (path == null || expectedHash == null || expectedHash.length() != 64) {
            throw new SecurityException("Missing MTZ path or SHA-256");
        }
        File file = new File(path).getCanonicalFile();
        if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".mtz")) {
            throw new SecurityException("MTZ staging file is absent");
        }
        File externalMiui = activity.getExternalFilesDir("MIUI");
        if (externalMiui == null) throw new SecurityException("Theme Manager external storage is unavailable");
        File expectedRoot = new File(externalMiui, "theme/.download").getCanonicalFile();
        if (!file.getPath().startsWith(expectedRoot.getPath() + File.separator)) {
            throw new SecurityException("MTZ is outside Theme Manager's import directory");
        }
        if (!sha256(file).equalsIgnoreCase(expectedHash)) {
            throw new SecurityException("MTZ SHA-256 changed before import");
        }
        return file;
    }

    private final class BridgeSession {
        private final Activity activity;
        private final ClassLoader classLoader;
        private final File themeFile;
        private final String expectedHash;
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final Runnable timeout = () -> finish(false, "Theme Manager import/apply timed out");
        private boolean receiverRegistered;

        private final BroadcastReceiver importReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // The receiver class lives in the module APK, while the serialized Resource is
                // owned by Theme Manager. Select the host class loader before unparcelling it.
                intent.setExtrasClassLoader(classLoader);
                Object resource = intent.getSerializableExtra(EXTRA_IMPORTED_RESOURCE);
                log(
                    Log.INFO,
                    TAG,
                    "Theme Manager 10.8 import callback: action=" + intent.getAction()
                        + ", path=" + resourceValue(resource, "getDownloadPath")
                        + ", localId=" + resourceValue(resource, "getLocalId")
                );
                if (resource == null || !matchesRequestedResource(resource)) return;
                if (IMPORT_COMPLETE.equals(intent.getAction())) {
                    unregisterReceiver();
                    activity.runOnUiThread(() -> applyImportedTheme(resource));
                } else if (IMPORT_FAILED.equals(intent.getAction())) {
                    finish(false, "Theme Manager rejected the MTZ during import");
                }
            }
        };

        BridgeSession(Activity activity, ClassLoader classLoader, File themeFile, String expectedHash) {
            this.activity = activity;
            this.classLoader = classLoader;
            this.themeFile = themeFile;
            this.expectedHash = expectedHash;
        }

        void start() throws Exception {
            IntentFilter filter = new IntentFilter();
            filter.addAction(IMPORT_COMPLETE);
            filter.addAction(IMPORT_FAILED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Xiaomi's own handler registers this receiver as exported. The import service can
                // run outside the activity process on module builds, so mirror that contract and
                // authenticate the callback against the request's canonical path/SHA-256 below.
                activity.registerReceiver(importReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                activity.registerReceiver(importReceiver, filter);
            }
            receiverRegistered = true;
            mainHandler.postDelayed(timeout, IMPORT_APPLY_TIMEOUT_MS);
            invokeThemeManagerImporter(activity, classLoader, themeFile);
        }

        private boolean matchesRequestedResource(Object resource) {
            try {
                Object value = resource.getClass().getMethod("getDownloadPath").invoke(resource);
                if (value == null) return false;
                File callbackFile = new File(value.toString()).getCanonicalFile();
                if (themeFile.getCanonicalPath().equals(callbackFile.getCanonicalPath())) return true;
                return callbackFile.isFile() && sha256(callbackFile).equalsIgnoreCase(expectedHash);
            } catch (Throwable error) {
                log(Log.WARN, TAG, "Unable to match import callback: " + concise(error));
                return false;
            }
        }

        private String resourceValue(Object resource, String getter) {
            if (resource == null) return "null";
            try {
                Object value = resource.getClass().getMethod(getter).invoke(resource);
                return String.valueOf(value);
            } catch (Throwable error) {
                return "<unavailable>";
            }
        }

        private void applyImportedTheme(Object resource) {
            if (finished.get()) return;
            try {
                Class<?> newContextClass = Class.forName(
                    "com.android.thememanager.basemodule.resource.NewResourceContext", false, classLoader
                );
                Object newThemeContext = newContextClass.getMethod("getTheme").invoke(null);
                Class<?> appInnerContextClass = Class.forName(
                    "com.android.thememanager.AppInnerContext", false, classLoader
                );
                Object appInnerContext = appInnerContextClass.getMethod("zy").invoke(null);
                Object contextManager = appInnerContextClass.getMethod("n").invoke(appInnerContext);
                Object resourceContext = contextManager.getClass()
                    .getMethod("g", newContextClass)
                    .invoke(contextManager, newThemeContext);

                Class<?> resourceClass = Class.forName(
                    "com.android.thememanager.basemodule.resource.model.Resource", false, classLoader
                );
                Class<?> resourceContextClass = Class.forName(
                    "com.android.thememanager.ResourceContext", false, classLoader
                );
                Class<?> applyInfoClass = Class.forName(
                    "com.android.thememanager.detail.theme.model.ApplyThemeInfo", false, classLoader
                );
                Object applyInfo = applyInfoClass.getConstructor().newInstance();
                invokeBooleanSetterIfPresent(applyInfo, "setShowProgress", false);
                invokeBooleanSetterIfPresent(applyInfo, "setShowToastOfSuccess", true);
                Class<?> applyUtils = Class.forName(
                    "com.android.thememanager.util.ThemeApplyUtils", false, classLoader
                );
                Method apply = applyUtils.getMethod(
                    "x2",
                    Activity.class,
                    resourceContextClass,
                    resourceClass,
                    applyInfoClass,
                    Runnable.class
                );
                apply.invoke(null, activity, resourceContext, resource, applyInfo, (Runnable) () -> finish(true, null));
            } catch (Throwable error) {
                finish(false, "Theme Manager apply failed: " + concise(error));
                log(Log.ERROR, TAG, "Imported MTZ could not be applied", error);
            }
        }

        private void finish(boolean success, String error) {
            if (!finished.compareAndSet(false, true)) return;
            mainHandler.removeCallbacks(timeout);
            unregisterReceiver();
            activity.runOnUiThread(() -> finishBridgeActivity(activity, success, error));
        }

        private void unregisterReceiver() {
            if (!receiverRegistered) return;
            receiverRegistered = false;
            try {
                activity.unregisterReceiver(importReceiver);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void invokeThemeManagerImporter(Activity activity, ClassLoader classLoader, File themeFile)
            throws Exception {
        Class<?> resourceClass = Class.forName(
            "com.android.thememanager.basemodule.resource.model.Resource", false, classLoader
        );
        Object resource = resourceClass.getConstructor().newInstance();
        resourceClass.getMethod("setDownloadPath", String.class).invoke(resource, themeFile.getPath());

        Class<?> newContextClass = Class.forName(
            "com.android.thememanager.basemodule.resource.NewResourceContext", false, classLoader
        );
        Object themeContext = newContextClass.getMethod("getTheme").invoke(null);
        Class<?> handlerClass = Class.forName(
            "com.android.thememanager.mine.local.resource.ThemeImportHandler", false, classLoader
        );
        Object handler = handlerClass.getConstructor(Context.class).newInstance(activity);
        Method importMethod = handlerClass.getMethod("n", newContextClass, resourceClass);
        importMethod.invoke(handler, themeContext, resource);
    }

    private static void invokeBooleanSetterIfPresent(Object target, String name, boolean value) {
        try {
            target.getClass().getMethod(name, boolean.class).invoke(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static void finishBridgeActivity(Activity activity, boolean success, String error) {
        Intent result = new Intent();
        if (success) {
            result.putExtra(ThemeManagerBridgeContract.EXTRA_RESULT, ThemeManagerBridgeContract.RESULT_OK);
            activity.setResult(Activity.RESULT_OK, result);
        } else {
            result.putExtra(ThemeManagerBridgeContract.EXTRA_ERROR, error == null ? "Unknown error" : error);
            activity.setResult(Activity.RESULT_CANCELED, result);
        }
        activity.finish();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        StringBuilder output = new StringBuilder(64);
        for (byte value : digest.digest()) output.append(String.format(Locale.ROOT, "%02x", value));
        return output.toString();
    }

    private void installSystemValidationHooks(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> drmManager = Class.forName("miui.drm.DrmManager", false, classLoader);
        Object success = drmSuccess(classLoader);

        List<Method> legalChecks = namedMethods(drmManager, "isLegal");
        legalChecks.removeIf(method -> !method.getReturnType().isInstance(success));
        if (legalChecks.isEmpty()) throw new NoSuchMethodException("No compatible DrmManager.isLegal method");

        for (Method method : legalChecks) {
            method.setAccessible(true);
            hook(method)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (Boolean.TRUE.equals(THEME_VALIDATION_IN_PROGRESS.get())) return success;
                    return chain.proceed();
                });
        }

        try {
            Class<?> themeReceiver = Class.forName("miui.drm.ThemeReceiver", false, classLoader);
            List<Method> validationMethods = namedMethods(themeReceiver, "validateTheme");
            for (Method method : validationMethods) {
                method.setAccessible(true);
                hook(method)
                    .setPriority(PRIORITY_HIGHEST)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Boolean previous = THEME_VALIDATION_IN_PROGRESS.get();
                        THEME_VALIDATION_IN_PROGRESS.set(true);
                        try {
                            return chain.proceed();
                        } finally {
                            if (previous == null) THEME_VALIDATION_IN_PROGRESS.remove();
                            else THEME_VALIDATION_IN_PROGRESS.set(previous);
                        }
                    });
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "ThemeReceiver hook optional: " + t.getMessage());
        }
    }

    private void installThemeManagerRightsHook(String apkPath, ClassLoader classLoader) throws Exception {
        System.loadLibrary("dexkit");
        Object success = drmSuccess(classLoader);
        List<Method> compatible = new ArrayList<>();
        try (DexKitBridge bridge = DexKitBridge.create(apkPath)) {
            MethodDataList matches = bridge.findMethod(
                FindMethod.create().matcher(
                    MethodMatcher.create().usingStrings(
                        "theme",
                        "ThemeManagerTag",
                        "/system",
                        "check rights isLegal: "
                    )
                )
            );
            if (matches.isEmpty()) {
                matches = bridge.findMethod(
                    FindMethod.create().matcher(
                        MethodMatcher.create().usingStrings("check rights")
                    )
                );
            }
            for (int index = 0; index < matches.size(); index++) {
                Method candidate = matches.get(index).getMethodInstance(classLoader);
                if (candidate.getReturnType().isInstance(success)) compatible.add(candidate);
            }
        }
        if (compatible.isEmpty()) {
            throw new NoSuchMethodException("Expected at least one Theme Manager rights check, found 0");
        }
        for (Method rightsCheck : compatible) {
            rightsCheck.setAccessible(true);
            hook(rightsCheck)
                .setPriority(PRIORITY_HIGHEST)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> success);
        }
    }

    private static List<Method> namedMethods(Class<?> type, String name) {
        List<Method> result = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && !Modifier.isAbstract(method.getModifiers())) {
                result.add(method);
            }
        }
        return result;
    }

    private static Object drmSuccess(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> result = Class.forName("miui.drm.DrmManager$DrmResult", false, classLoader);
        return result.getField("DRM_SUCCESS").get(null);
    }

    private void writeMarkerFile(String fileName, boolean ready, String error) {
        String content = "ready=" + ready + "\nversion=" + BuildConfig.VERSION_CODE + "\ntimestamp=" + System.currentTimeMillis() + (error != null ? "\nerror=" + error : "");
        java.util.List<java.io.File> candidates = new java.util.ArrayList<>();
        // Theme application replaces /data/system/theme, so Theme Manager process markers must
        // prefer the app's persistent files directory. system_server naturally falls back below.
        candidates.add(new java.io.File("/data/data/com.android.thememanager/files", fileName));
        candidates.add(new java.io.File("/data/user/0/com.android.thememanager/files", fileName));
        candidates.add(new java.io.File("/data/system/theme", fileName));
        candidates.add(new java.io.File("/data/local/tmp", fileName));

        for (java.io.File file : candidates) {
            try {
                java.io.File dir = file.getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
                try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                    writer.write(content);
                }
                file.setReadable(true, false);
                file.setWritable(true, false);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private static String concise(Throwable error) {
        String message = error.getMessage();
        String value = error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return value.length() <= 240 ? value : value.substring(0, 240);
    }
}
