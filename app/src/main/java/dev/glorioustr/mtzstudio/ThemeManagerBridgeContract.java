package dev.glorioustr.mtzstudio;

/** Shared, authenticated request contract for the Theme Manager 10.8 in-process bridge. */
public final class ThemeManagerBridgeContract {
    public static final String ACTION_APPLY_10_8 =
            "dev.glorioustr.mtzstudio.action.APPLY_THEME_MANAGER_10_8";
    public static final String EXTRA_THEME_PATH =
            "dev.glorioustr.mtzstudio.extra.THEME_PATH";
    public static final String EXTRA_THEME_SHA256 =
            "dev.glorioustr.mtzstudio.extra.THEME_SHA256";
    public static final String EXTRA_RESULT =
            "dev.glorioustr.mtzstudio.extra.APPLY_RESULT";
    public static final String EXTRA_ERROR =
            "dev.glorioustr.mtzstudio.extra.APPLY_ERROR";
    public static final String RESULT_OK = "ok";
    public static final String BRIDGE_MARKER = "mtz_import_bridge";

    private ThemeManagerBridgeContract() {
    }
}
