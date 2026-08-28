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

## Startup behavior

The app queries `com.android.thememanager` through Android `PackageManager`. Versions `2.15.5.46` and `3.0.5.6` use the exported legacy tester contract. The request is intentionally frozen to the `support3.0` action, the `ApplyThemeForScreenshot` alias, and the original four extras verified on a `3.0.5.6-global` device. Additional path keys, apply booleans, or activity flags can make that version return without applying the MTZ.

Theme Manager `10.8.7.6` support is reserved for the separately validated `v2.0.0` module-compatible release. Until it is tested on a physical device, `v1.2.0` reports it as unverified rather than guessing compatibility. The MTZ library and composer remain usable regardless of this result.

## Root downgrade boundary

HyperOS MTZ Studio does not download or bundle Xiaomi APKs. The user must select an APK they are entitled to use. Before enabling the root action, the app checks:

1. The installed Theme Manager package exists.
2. The APK package exactly matches `com.android.thememanager`.
3. The canonical APK version is exactly `2.15.5.46`.
4. The APK signing certificate matches the installed Theme Manager signing history.
5. The staged APK remains unchanged according to SHA-256.
6. The user accepts the risk and confirms the exact target a second time.

The fixed root command is `pm install -r -d --user 0 -S <verified-size> -`; verified APK bytes are streamed over stdin. No file path or user-controlled text enters the command. The app does not uninstall Theme Manager, write system partitions, disable package verification, clear app data, or attempt an invasive fallback. Some production ROMs can still reject downgrades; this is reported as a normal failure.
