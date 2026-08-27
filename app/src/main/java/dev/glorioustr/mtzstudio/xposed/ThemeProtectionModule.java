package dev.glorioustr.mtzstudio.xposed;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import dev.glorioustr.mtzstudio.BuildConfig;
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
        try {
            installThemeManagerRightsHook(
                param.getApplicationInfo().sourceDir,
                param.getClassLoader()
            );
            writeMarkerFile("mtz_protection_thememanager", true, null);
            log(Log.INFO, TAG, "Theme Manager rights protection is ready");
        } catch (Throwable error) {
            writeMarkerFile("mtz_protection_thememanager", false, concise(error));
            log(Log.ERROR, TAG, "Theme Manager rights hook was not installed", error);
        }
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
        candidates.add(new java.io.File("/data/system/theme", fileName));
        candidates.add(new java.io.File("/data/data/com.android.thememanager/files", fileName));
        candidates.add(new java.io.File("/data/user/0/com.android.thememanager/files", fileName));
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
