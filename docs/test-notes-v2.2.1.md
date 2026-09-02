# MTZ Studio v2.2.1 — private test build

This APK is for affected-device testing, not a GitHub release. The reported native import rejection is not yet reproduced locally; no claim of universal compatibility is made.

## Changes

- Drain `su` and Shizuku user-service command output while the process runs, retaining a bounded diagnostic tail. Waiting before reading could deadlock on a full output pipe.
- Serialize Shizuku user-service lifetimes across callers so one command cannot destroy another's service; version the service with the APK to avoid stale code after updates.
- Prevent overlapping create/import/apply/delete actions in the active Studio screen; release the busy state on failure, including failures to launch the host activity.
- Preserve the native import contract observed in the supplied Theme Manager 10.8.7.6 APK. Remove speculative Resource metadata setters that do not exist in that APK.
- Observe native importer/unzip exceptions only for authenticated Studio import sessions. Include the host error type in the returned diagnostics when the internal methods are available. Optional observation failure must not disable the bridge, suppress host errors or replay operations.
- Treat unsuccessful diagnostic commands as unavailable, not successfully collected logs.
- Retain the pending workspace fixes: dynamic Xposed scope (`staticScope=false`), modern Themes-only scope, skipping invalid catalog records, rootless local workspace and Shizuku-compatible authorization.

## Scope and limitations

No APK Protection Patch is installed or embedded. Android framework files, APK signatures, root-module mounts and device settings are not changed. A generic `null: cannot import` toast does not establish that an APK-signature patch is needed.

The supplied native APK's manual ImportResourceTask constructs a Resource with only a download path before using the same import manager as Studio. ThemeImportService deletes its staged archive on either success or failure, and ThemeImportManager omits its detailed failure message from the failure broadcast. Consequently an empty title/local ID is not by itself evidence of missing metadata in Studio's invocation. The private MTZ source/public export must remain the basis for any retry.

## Test procedure

1. Install this APK as an update without clearing Studio or Xiaomi Themes data. It uses the stable v2.2.0 release certificate. If an earlier test APK used another certificate, stop on a signature conflict; do not uninstall without a library backup.
2. Open Studio. On modern Theme Manager builds, approve only the Themes scope if requested by Vector/LSPosed. System Framework is not required for this provider.
3. Restart the device once to load the new Xposed code, then retry one affected MTZ. Do not install another protection patch at the same time.
4. If import fails, export the new Live Diagnostics report. Look for the native importer stage/error and host log availability, rather than interpreting the generic toast as a specific cause.

Local checks: all Gradle unit-test tasks (95 test executions including Android library build variants), translation validation, debug/release lint and signed release build passed. The new process-output tests exercise a real child process producing over 1 MiB of output and timeout termination. APK version 2.2.1/code 11, `staticScope=false` and the stable signing certificate were verified in the final package. No ADB device was connected for an end-to-end installation/import test.

The optional hooks preserve exceptions from the original method, consistent with the [libxposed exception-mode contract](https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.ExceptionMode.html); they do not retry the import.
