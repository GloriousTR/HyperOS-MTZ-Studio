# HyperOS MTZ Studio v3.6.0

v3.6.0 makes Xiaomi Themes compatibility visible before a user spends time importing a theme.

## What changed

- Added a shared **Theme Manager compatibility** card to the MTZ Import area for Root, Shizuku/Shevery and unauthorised sessions.
- The card inspects the installed Xiaomi Themes package for a declared local-MTZ entry point. It no longer assumes removed internal activities such as `ApplyThemeForScreenshot` are available.
- Unsupported or unverified Themes builds now show a clear warning explaining that imported MTZ files may not reach Xiaomi Themes' local library or apply automatically.
- The warning includes a one-tap APKMirror download-start link for the verified Xiaomi Themes `3.0.5.6-global` universal APK.
- Root mode retains the optional verified downgrade workflow: the user selects the downloaded APK, Studio verifies package identity, target version and the Xiaomi signing certificate, then requires confirmation before using the system package manager.
- Shizuku/Shevery in wireless-ADB mode intentionally does not downgrade or replace the Xiaomi system package.

## Requirements

- Android 8.0 or newer.
- Root is required for Studio's in-app Theme Manager downgrade workflow.
- Shizuku/Shevery remains the recommended method for rootsuz theme operations; Android's normal installer handles any user-chosen Themes APK installation.
