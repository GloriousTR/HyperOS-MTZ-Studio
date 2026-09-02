# MTZ Studio v2.2.2 — responsiveness test APK

Private test build (version code 12). Not published to GitHub. The affected user's 2.2.1 report showed working root and successful private MTZ validation/commit, but an unfinished catalog scan and a 22-second Vector CLI preparation ending in exit 127. It did not contain a completed native import result or an ANR trace.

## Changes since the v2.2.1 test

- Removed per-operation Vector CLI preparation entirely. Apply/import/delete no longer enable the module, remove scopes, erase readiness markers or force-stop Xiaomi Themes. Read-only scope confirmation uses the standard Xposed service with a bounded wait; fallback readiness markers must match this APK version.
- Modern mode no longer silently removes the user's System Framework scope or clears protection preferences on every app start. It requests only Themes. System Framework remains unnecessary for the modern provider.
- Private MTZ selection/import no longer waits for root preflight. Root remains verified for operations that actually access Xiaomi's private files. Failure preparing a native operation retains the private source for retry.
- Existing private themes/drafts remain visible even if native catalog loading is incomplete. Catalog progress is published after each record so the first reconstructed themes can appear before the entire scan completes.
- Foreground create/import/apply/delete requests pause further background catalog items. An in-flight item may finish first; background file-copy commands have a 30-second execution limit and scans stop between items after a 90-second work budget. Incomplete scans do not detach origins as though the scan had finished. Retry resumes from already imported records.
- Command queue acquisition is bounded to three seconds rather than an indefinite monitor wait. Shizuku binding is limited to ten seconds; remote execution has its own response deadline. A timed-out dispatched operation is never automatically replayed. Service removal is also bounded. These limits do not assert that an unresponsive remote operation was rolled back.
- The catalog shows its current processed/total count alongside progress. No new system patches or root modules are installed.
- A failed/cancelled native handoff now displays an error in Studio instead of automatically reopening Xiaomi Themes. Manual opening remains an explicit user action.

## Device test

Install as an update over the stable-signed 2.2.0/2.2.1 APK without clearing data. Open Studio and allow the Themes scope if requested. Restart once to load the updated Xposed code, then try one affected MTZ. System Framework does not need to be selected for Theme Manager 10.8.7.6.

Check that the file picker opens promptly, cached/newly read themes appear during scanning, and the native handoff either completes or reports an actionable failure. If it fails, export a fresh Live Diagnostics report after the result. The original native import rejection remains unverified until tested on the affected device.

## Local verification

105 unit-test executions across JVM and Android library variants passed, including command queue saturation (no dispatch on timeout), lock release on failure, remote result/error preservation and stalled remote call timeout without replay. Translation validation and release lint/build passed. No ADB device was listed at the final connection check; no phone installation or end-to-end native import was performed.
