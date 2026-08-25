# Theme Manager compatibility

HyperOS MTZ Studio is an independent project. Its compatibility policy is defined locally and has no dependency on another application or repository.

## Global-version matrix

| Canonical version | Observed tester interpretation |
| --- | --- |
| `2.15.5.46` | Imports the MTZ as an independent local theme; recommended |
| `3.0.5.14` | Applies a temporary/composite result over “Default” |
| `3.0.6.8` | Tester activity is removed |

Suffixes such as `-global` are ignored only for matrix matching. Unknown versions are shown as unverified rather than guessed.

## Startup behavior

The app queries `com.android.thememanager` through Android `PackageManager`. When the canonical installed version is not `2.15.5.46`, it recommends the compatible version and explains the detected behavior. The MTZ library and composer remain usable regardless of this result.

## Root downgrade boundary

HyperOS MTZ Studio does not download or bundle Xiaomi APKs. The user must select an APK they are entitled to use. Before enabling the root action, the app checks:

1. The installed Theme Manager package exists.
2. The APK package exactly matches `com.android.thememanager`.
3. The canonical APK version is exactly `2.15.5.46`.
4. The APK signing certificate matches the installed Theme Manager signing history.
5. The staged APK remains unchanged according to SHA-256.
6. The user accepts the risk and confirms the exact target a second time.

The fixed root command is `pm install -r -d --user 0 -S <verified-size> -`; verified APK bytes are streamed over stdin. No file path or user-controlled text enters the command. The app does not uninstall Theme Manager, write system partitions, disable package verification, clear app data, or attempt an invasive fallback. Some production ROMs can still reject downgrades; this is reported as a normal failure.
