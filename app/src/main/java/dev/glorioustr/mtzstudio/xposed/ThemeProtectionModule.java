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
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
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
            if (hasModernThemeManagerImportSurface(param.getClassLoader())) {
                installThemeManagerImportBridge(param.getClassLoader());
                writeMarkerFile(ThemeManagerBridgeContract.BRIDGE_MARKER, true, null);
                log(Log.INFO, TAG, "Modern Theme Manager bridge is ready; Global rights protection is not required");
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

    private static boolean hasModernThemeManagerImportSurface(ClassLoader classLoader) {
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

    /** Modern Theme Manager bridge: native catalog import, apply and delete operations. */
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
                if (request != null && isModernBridgeAction(request.getAction())) {
                    activity.runOnUiThread(() -> beginModernThemeManagerRequest(activity, classLoader, request));
                }
                return result;
            });
    }

    private static boolean isModernBridgeAction(String action) {
        return ThemeManagerBridgeContract.ACTION_APPLY_MODERN.equals(action)
            || ThemeManagerBridgeContract.ACTION_IMPORT_MODERN.equals(action)
            || ThemeManagerBridgeContract.ACTION_APPLY_EXISTING.equals(action)
            || ThemeManagerBridgeContract.ACTION_DELETE_EXISTING.equals(action);
    }

    private void beginModernThemeManagerRequest(Activity activity, ClassLoader classLoader, Intent request) {
        try {
            if (!BuildConfig.APPLICATION_ID.equals(activity.getCallingPackage())) {
                throw new SecurityException("Untrusted Theme Manager bridge caller");
            }
            String action = request.getAction();
            bridgeTrace(activity, "İstek alındı; çağıran doğrulandı: " + action);
            if (ThemeManagerBridgeContract.ACTION_APPLY_EXISTING.equals(action)
                    || ThemeManagerBridgeContract.ACTION_DELETE_EXISTING.equals(action)) {
                String localId = validateLocalId(
                    request.getStringExtra(ThemeManagerBridgeContract.EXTRA_THEME_LOCAL_ID)
                );
                Object resource = loadExistingThemeResource(activity, classLoader, localId);
                bridgeTrace(activity, "Yerel tema kaydı çözümlendi: " + localId);
                if (ThemeManagerBridgeContract.ACTION_DELETE_EXISTING.equals(action)) {
                    deleteExistingTheme(activity, classLoader, resource, localId);
                } else {
                    applyThemeResource(activity, classLoader, resource,
                        () -> finishBridgeActivity(activity, true, null, localId));
                }
                return;
            }
            String path = request.getStringExtra(ThemeManagerBridgeContract.EXTRA_THEME_PATH);
            String expectedHash = request.getStringExtra(ThemeManagerBridgeContract.EXTRA_THEME_SHA256);
            File themeFile = validateBridgeThemeFile(activity, path, expectedHash);
            bridgeTrace(activity, "MTZ yolu ve SHA-256 doğrulandı");
            boolean applyAfterImport = ThemeManagerBridgeContract.ACTION_APPLY_MODERN.equals(action);
            new BridgeSession(activity, classLoader, themeFile, expectedHash, applyAfterImport).start();
        } catch (Throwable error) {
            finishBridgeActivity(activity, false, concise(error), null);
            log(Log.ERROR, TAG, "Modern Theme Manager request rejected", error);
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
        private final boolean applyAfterImport;
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
                    "Modern Theme Manager import callback: action=" + intent.getAction()
                        + ", path=" + resourceValue(resource, "getDownloadPath")
                        + ", localId=" + resourceValue(resource, "getLocalId")
                );
                if (resource == null || !matchesRequestedResource(resource)) return;
                bridgeTrace(activity, "İçe aktarma yanıtı doğrulandı: " + intent.getAction());
                if (IMPORT_COMPLETE.equals(intent.getAction())) {
                    unregisterReceiver();
                    activity.runOnUiThread(() -> {
                        if (applyAfterImport) {
                            applyImportedTheme(resource);
                        } else {
                            try {
                                finish(true, null, resolvedLocalId(resource));
                            } catch (Throwable error) {
                                finish(false, "Imported theme identity is unavailable: " + concise(error));
                            }
                        }
                    });
                } else if (IMPORT_FAILED.equals(intent.getAction())) {
                    finish(false, "Theme Manager rejected the MTZ during import");
                }
            }
        };

        BridgeSession(Activity activity, ClassLoader classLoader, File themeFile, String expectedHash,
                boolean applyAfterImport) {
            this.activity = activity;
            this.classLoader = classLoader;
            this.themeFile = themeFile;
            this.expectedHash = expectedHash;
            this.applyAfterImport = applyAfterImport;
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
            bridgeTrace(activity, "Yerleşik MTZ içe aktarma başlatılıyor");
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
                String localId = resolvedLocalId(resource);
                applyThemeResource(activity, classLoader, resource, () -> finish(true, null, localId));
            } catch (Throwable error) {
                finish(false, "Theme Manager apply failed: " + concise(error));
                log(Log.ERROR, TAG, "Imported MTZ could not be applied", error);
            }
        }

        private void finish(boolean success, String error) {
            finish(success, error, null);
        }

        private void finish(boolean success, String error, String localId) {
            if (!finished.compareAndSet(false, true)) return;
            mainHandler.removeCallbacks(timeout);
            unregisterReceiver();
            activity.runOnUiThread(() -> finishBridgeActivity(activity, success, error, localId));
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

    private static void applyThemeResource(Activity activity, ClassLoader classLoader, Object resource,
            Runnable completion) throws Exception {
        Class<?> newContextClass = Class.forName(
            "com.android.thememanager.basemodule.resource.NewResourceContext", false, classLoader
        );
        Object resourceContext = themeResourceContext(classLoader, newContextClass);
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
            "x2", Activity.class, resourceContextClass, resourceClass, applyInfoClass, Runnable.class
        );
        bridgeTrace(activity, "Yerleşik tema uygulama başlatılıyor");
        Runnable tracedCompletion = () -> {
            bridgeTrace(activity, "Temalar uygulama tamamlanma çağrısını gönderdi");
            completion.run();
        };
        apply.invoke(null, activity, resourceContext, resource, applyInfo, tracedCompletion);
    }

    private static Object loadExistingThemeResource(Activity activity, ClassLoader classLoader, String localId)
            throws Exception {
        File externalMiui = activity.getExternalFilesDir("MIUI");
        if (externalMiui == null) throw new IllegalStateException("Theme Manager storage is unavailable");
        File metadataRoot = new File(externalMiui, "theme/.data/meta/theme").getCanonicalFile();
        File metadata = new File(metadataRoot, localId + ".mrm").getCanonicalFile();
        if (!metadata.getPath().startsWith(metadataRoot.getPath() + File.separator) || !metadata.isFile()) {
            throw new IllegalArgumentException("Theme Manager theme record was not found");
        }
        Class<?> newContextClass = Class.forName(
            "com.android.thememanager.basemodule.resource.NewResourceContext", false, classLoader
        );
        Object resourceContext = themeResourceContext(classLoader, newContextClass);
        Class<?> resourceClass = Class.forName(
            "com.android.thememanager.basemodule.resource.model.Resource", false, classLoader
        );
        Class<?> resourceContextClass = Class.forName(
            "com.android.thememanager.ResourceContext", false, classLoader
        );
        Class<?> miuiUtilsClass = Class.forName(
            "com.android.thememanager.basemodule.utils.MiuiUtils", false, classLoader
        );
        Method metadataLoader = null;
        for (Method candidate : miuiUtilsClass.getDeclaredMethods()) {
            Class<?>[] parameters = candidate.getParameterTypes();
            if (Modifier.isStatic(candidate.getModifiers())
                    && resourceClass.isAssignableFrom(candidate.getReturnType())
                    && parameters.length == 2
                    && parameters[0] == String.class
                    && parameters[1] == resourceContextClass) {
                metadataLoader = candidate;
                break;
            }
        }
        if (metadataLoader == null) {
            throw new NoSuchMethodException("Theme Manager metadata loader is unavailable");
        }
        metadataLoader.setAccessible(true);
        Object resource = metadataLoader.invoke(null, metadata.getAbsolutePath(), resourceContext);
        if (resource == null || !localId.equals(String.valueOf(resourceClass.getMethod("getLocalId").invoke(resource)))) {
            throw new SecurityException("Theme Manager record identity mismatch");
        }
        return resource;
    }

    private static Object themeResourceContext(ClassLoader classLoader, Class<?> newContextClass) throws Exception {
        Object newThemeContext = newContextClass.getMethod("getTheme").invoke(null);
        Class<?> appInnerContextClass = Class.forName(
            "com.android.thememanager.AppInnerContext", false, classLoader
        );
        Object appInnerContext = appInnerContextClass.getMethod("zy").invoke(null);
        Object contextManager = appInnerContextClass.getMethod("n").invoke(appInnerContext);
        return contextManager.getClass().getMethod("g", newContextClass)
            .invoke(contextManager, newThemeContext);
    }

    private static void deleteExistingTheme(Activity activity, ClassLoader classLoader, Object resource,
            String localId) throws Exception {
        File externalMiui = activity.getExternalFilesDir("MIUI");
        if (externalMiui == null) throw new IllegalStateException("Theme Manager storage is unavailable");
        File metadata = new File(externalMiui, "theme/.data/meta/theme/" + localId + ".mrm").getCanonicalFile();
        Class<?> newContextClass = Class.forName(
            "com.android.thememanager.basemodule.resource.NewResourceContext", false, classLoader
        );
        Object themeContext = newContextClass.getMethod("getTheme").invoke(null);
        Class<?> listenerClass = Class.forName(
            "com.android.thememanager.basemodule.local.ResourceDeleteListener", false, classLoader
        );
        AtomicBoolean completed = new AtomicBoolean(false);
        Object listener = Proxy.newProxyInstance(classLoader, new Class<?>[]{listenerClass}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return args != null && args.length == 1 && proxy == args[0];
                    case "toString": return "MtzStudioResourceDeleteListener";
                    default: return null;
                }
            }
            if (completed.compareAndSet(false, true)) {
                boolean removed = !metadata.exists();
                bridgeTrace(activity, "Silme yanıtı geldi; yerel kayıt kaldırıldı=" + removed);
                activity.runOnUiThread(() -> finishBridgeActivity(
                    activity,
                    removed,
                    removed ? null : "Theme Manager kept this protected theme",
                    removed ? localId : null
                ));
            }
            return null;
        });
        Class<?> taskClass = Class.forName(
            "com.android.thememanager.controller.local.DeleteResourceTask", false, classLoader
        );
        Object task = taskClass.getConstructor(Activity.class, List.class, newContextClass, listenerClass)
            .newInstance(activity, Collections.singletonList(resource), themeContext, listener);
        // Reflection erases execute's signature, but DeleteResourceTask's generated
        // doInBackground bridge still casts the array to Void[]. Object[] crashes
        // the host process on the worker thread, beyond this method's try/catch.
        bridgeTrace(activity, "Yerleşik tema silme başlatılıyor: " + localId);
        AsyncTask.class.getMethod("execute", Object[].class).invoke(task, (Object) new Void[0]);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (completed.compareAndSet(false, true)) {
                finishBridgeActivity(activity, false, "Theme Manager delete timed out", null);
            }
        }, IMPORT_APPLY_TIMEOUT_MS);
    }

    private static String validateLocalId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new SecurityException("Invalid Theme Manager local ID");
        }
        return value;
    }

    private static String resolvedLocalId(Object resource) throws Exception {
        Object value = resource.getClass().getMethod("getLocalId").invoke(resource);
        return validateLocalId(value == null ? null : value.toString());
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

    private static void finishBridgeActivity(Activity activity, boolean success, String error, String localId) {
        bridgeTrace(activity, success ? "Köprü işlemi tamamlandı" : "Köprü hatası: " + error);
        Intent result = new Intent();
        result.putStringArrayListExtra(ThemeManagerBridgeContract.EXTRA_DIAGNOSTIC_TRACE,
            activity.getIntent().getStringArrayListExtra(ThemeManagerBridgeContract.EXTRA_DIAGNOSTIC_TRACE));
        if (success) {
            result.putExtra(ThemeManagerBridgeContract.EXTRA_RESULT, ThemeManagerBridgeContract.RESULT_OK);
            if (localId != null) {
                result.putExtra(ThemeManagerBridgeContract.EXTRA_THEME_LOCAL_ID, localId);
            }
            activity.setResult(Activity.RESULT_OK, result);
        } else {
            result.putExtra(ThemeManagerBridgeContract.EXTRA_ERROR, error == null ? "Unknown error" : error);
            activity.setResult(Activity.RESULT_CANCELED, result);
        }
        activity.finish();
    }

    private static void bridgeTrace(Activity activity, String message) {
        // Bounded trace travels back through the existing authenticated result channel.
        // Logcat retains intermediate steps if the host crashes before returning a result.
        try {
            String line = System.currentTimeMillis() + " · " + message;
            ArrayList<String> trace = activity.getIntent().getStringArrayListExtra(ThemeManagerBridgeContract.EXTRA_DIAGNOSTIC_TRACE);
            if (trace == null) trace = new ArrayList<>();
            if (trace.size() < 40) trace.add(line);
            activity.getIntent().putStringArrayListExtra(ThemeManagerBridgeContract.EXTRA_DIAGNOSTIC_TRACE, trace);
            Log.i(TAG, line);
            android.os.ResultReceiver receiver = activity.getIntent().getParcelableExtra(ThemeManagerBridgeContract.EXTRA_DIAGNOSTIC_RECEIVER);
            if (receiver != null) {
                Bundle data = new Bundle();
                data.putString("step", line);
                receiver.send(0, data);
            }
        } catch (Throwable ignored) {
            // Diagnostic collection must not affect native theme operations.
        }
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
