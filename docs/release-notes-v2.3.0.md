# HyperOS MTZ Studio v2.3.0

## Safer Xiaomi Themes catalog access

- Modern Xiaomi Themes libraries are no longer reconstructed automatically at startup.
- **Import a theme from Theme Manager** is now the default path for selecting only the themes you need.
- **Show all themes in Theme Manager** reads the native theme count first. If there are more than 24 themes, Studio explains the possible resource and stability impact and requires confirmation.
- A confirmed full-library reconstruction runs in resumable batches. Foreground MTZ import, compose and apply operations pause it until they finish.

## Compatibility diagnostics

- Live Diagnostics can explicitly export the installed Xiaomi Themes base APK on rooted devices.
- The exported file is verified against the installed package name, version and SHA-256 before the Android share sheet is opened.
- Nothing is uploaded, installed or shared automatically.
- Runtime diagnostics record the detected Theme Manager behavior, legacy tester availability and split-package count to help investigate new ROM branches.

## Preserved behavior

- Global `2.15.5.46` and `3.0.5.6` flows are unchanged.
- Modern rooted import, apply and delete flows remain unchanged.
- Rootless local import, editing, composition, export and manual Xiaomi Themes hand-off remain available.
