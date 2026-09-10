# HyperOS MTZ Studio v3.5.0

v3.5.0 completes the account-backed cloud backup workflow and polishes translation and app navigation.

## What changed

- Google Drive backups now use MTZ Studio's private app-data area and follow the selected Google account across reinstallations and devices.
- Google OAuth access tokens are not persisted, and ordinary Drive files are outside the requested permission scope.
- Cloud backup and restore continue in the background with completion notifications and clearer Live Diagnostics events.
- Translation progress appears as a taller live percentage bar inside the Translate button and continues while the app is minimized.
- The AI provider list opens downward from the selected provider and remains scrollable on smaller displays.
- Added a foreground **Check for updates** action and retained signature, package, version and checksum verification before installation.
- **Check for updates** now appears directly above **About**, and **About** is the final app-menu item.
- Updated the bundled 20-language interface text for the cloud and update flows.

## Requirements

- Android 8.0 or newer.
- Root, or Shizuku/Shevery authorization, is required for privileged Xiaomi Themes operations.
- A Google account is required only when Google Drive cloud backup is selected.
