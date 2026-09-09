# HyperOS MTZ Studio v3.4.0

v3.4.0 improves long-running translation, makes cloud backups genuinely portable, and introduces a secure in-app update path.

## What’s new

- Theme cards now show live translation progress between the Apply and Translate/Delete controls.
- Translation continues while Studio is minimized and remains visible in Android notifications.
- Cloud backups are written to a folder selected from Google Drive, OneDrive or another Android document provider.
- Selecting the same cloud folder on a new device makes the latest Studio backup available for restore.
- HTTPS WebDAV and Nextcloud now perform real upload/download transfers; saved passwords are encrypted with Android Keystore.
- Cloud backup and restore continue in the background with progress and completion notifications.
- Studio periodically checks the official GitHub release channel, downloads newer APKs, and verifies SHA-256, package name, version code and signing certificate before installation.
- Update and cloud-folder messages are available across all 20 supported interface languages.
- Android 8–9 compatibility checks were strengthened for package signatures and Xiaomi Themes metadata.

## Update installation

Studio downloads only public, non-prerelease APKs from this repository. Android requires the user to approve the final package-installation screen. This confirmation is intentionally not bypassed.

## Cloud migration note

Earlier builds stored the so-called cloud backup inside the app’s private data. Android removed that file when the app was uninstalled. v3.4.0 replaces it with persistent user-selected cloud storage; choose the same folder again after installing Studio on another device.

## Requirements

- Android 8.0 or newer.
- Root, or Shizuku/Shevery authorization, is required for privileged Xiaomi Themes operations.
- Internet access is required only for online translation providers, WebDAV/cloud-provider transfers and update checks.
