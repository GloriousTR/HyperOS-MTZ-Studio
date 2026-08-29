# Theme Manager compatibility

HyperOS MTZ Studio is an independent project. Its compatibility policy is defined locally and has no dependency on another application or repository.

## Global-version matrix

| Canonical version | Observed tester interpretation |
| --- | --- |
| `2.15.5.46` | Imports the MTZ as an independent local theme; recommended |
| `3.0.5.6` | Imports the MTZ through the device-verified legacy tester contract |
| `3.0.5.14` | Applies a temporary/composite result over “Default” |
| `3.0.6.8` | Tester activity is removed |

Suffixes such as `-global` are ignored only for matrix matching. Unknown versions are shown as unverified rather than guessed.

## Module-version matrix

| Canonical version | v2.0.0 behavior |
| --- | --- |
| `10.8.7.6` | Uses the device-verified native Theme Manager importer and apply bridge. The installed HyperOS Theme Manager module provides persistent third-party theme use, so the separate Global Theme Protection path is disabled. |

## Startup behavior

The app queries `com.android.thememanager` through Android `PackageManager`. Versions `2.15.5.46` and `3.0.5.6` use the exported legacy tester contract. The request is intentionally frozen to the `support3.0` action, the `ApplyThemeForScreenshot` alias, and the original four extras verified on a `3.0.5.6-global` device. Additional path keys, apply booleans, or activity flags can make that version return without applying the MTZ.

When Theme Manager `10.8.7.6` is detected, v2.0.0 switches to its dedicated module-compatible protocol. MTZ Studio verifies the source archive, stages it in Theme Manager's local download area and opens the real local-theme activity. The in-process bridge invokes Theme Manager's native importer, waits for the imported `Resource`, applies it through Theme Manager and returns a verified result to MTZ Studio.

This path was validated on a physical device with Theme Manager `10.8.7.6` and Vector API 102. It requires root plus an enabled modern Xposed environment. Only the `com.android.thememanager` scope is needed: v2.0.0 removes the redundant `system` scope and hides the Global Theme Protection menu for this version. Vector scope preparation can be completed by the app through its privileged runner; compatible LSPosed forks can use their normal scope approval flow.

The legacy 2.15/3.0 Global protocol remains frozen to its v1.2.0 behavior and is not routed through the 10.8 bridge. The MTZ library and composer remain usable when the installed Theme Manager version is unknown.

## Root downgrade boundary

HyperOS MTZ Studio does not download or bundle Xiaomi APKs. The user must select an APK they are entitled to use. Before enabling the root action, the app checks:

1. The installed Theme Manager package exists.
2. The APK package exactly matches `com.android.thememanager`.
3. The canonical APK version is exactly `2.15.5.46`.
4. The APK signing certificate matches the installed Theme Manager signing history.
5. The staged APK remains unchanged according to SHA-256.
6. The user accepts the risk and confirms the exact target a second time.

The fixed root command is `pm install -r -d --user 0 -S <verified-size> -`; verified APK bytes are streamed over stdin. No file path or user-controlled text enters the command. The app does not uninstall Theme Manager, write system partitions, disable package verification, clear app data, or attempt an invasive fallback. Some production ROMs can still reject downgrades; this is reported as a normal failure.
