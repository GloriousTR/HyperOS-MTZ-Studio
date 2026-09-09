# HyperOS MTZ Studio

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/logo_banner.png" alt="HyperOS MTZ Studio" width="760">
</p>

<p align="center">
  <strong>A modern MTZ workspace for Xiaomi HyperOS and MIUI.</strong><br>
  Import, convert, translate, preview, compose and apply themes from one app.
</p>

<p align="center">
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/GloriousTR/HyperOS-MTZ-Studio?display_name=tag&style=for-the-badge&color=7357e6"></a>
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/actions/workflows/release.yml"><img alt="Release build" src="https://img.shields.io/github/actions/workflow/status/GloriousTR/HyperOS-MTZ-Studio/release.yml?style=for-the-badge&label=Release"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white">
</p>

<p align="center">
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/tag/v3.4.0"><strong>Download v3.4.0</strong></a>
  · <a href="docs/release-notes-v3.4.0.md">Release notes</a>
  · <a href="docs/theme-manager-compatibility.md">Compatibility</a>
  · <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/issues">Report an issue</a>
</p>

## What it does

- **MTZ library:** safely imports, validates, previews and organizes complete themes, icons and fonts.
- **BAK Converter:** converts supported Xiaomi Themes `.bak` archives directly into portable MTZ packages without replacing Xiaomi Themes data. Results appear immediately in the Studio library.
- **Theme Language Tool:** detects visible theme text and translates it into the app language. Reviewed Chinese terminology, safe MAML handling, translation memory and optional user-configured API providers improve natural results.
- **Background operations:** translation and cloud transfers continue while Studio is minimized, with live card and notification progress.
- **Portable cloud backup:** stores the Studio library in a user-selected Google Drive, OneDrive or document-provider folder, with optional HTTPS WebDAV/Nextcloud support.
- **Secure updates:** checks GitHub releases in the background, downloads newer APKs and verifies their checksum, package identity, version and signing certificate before opening Android's installer.
- **Theme Composer:** combines a base theme with selected icons, lock screen, status bar, dialer, messages, launcher, AOD, font and wallpapers while preserving untouched components.
- **Adaptive application:** uses the proven Root path or a Shizuku/Shevery-authorized rootless path for the installed Xiaomi Themes family.
- **Live Diagnostics:** records import, conversion, translation, composition and apply stages for compatibility analysis.

## v3.4.0 highlights

- Added per-theme translation progress between the Apply and Translate/Delete controls.
- Translation continues in a foreground background task and reports progress in Android notifications.
- Replaced device-local “cloud” storage with a real persistent cloud-folder workflow for cross-device restore.
- Added real HTTPS WebDAV/Nextcloud upload and restore; credentials are protected by Android Keystore.
- Cloud backup and restore now continue when the app is minimized and report completion through notifications.
- Added automatic GitHub update checks and secure APK downloading. Android still asks for final installation confirmation.
- Updated all 20 interface languages for cloud folders and update notifications.

## Existing capabilities

- Shizuku/Shevery is now required for applying themes without root; unreliable standard-rootless Xiaomi hand-offs are no longer attempted.
- Detects an installed Shizuku or Shevery manager and opens it directly when authorization is not ready.
- When neither manager is installed, Studio recommends Shevery and links to its official GitHub releases page.
- Includes a numbered Wireless debugging pairing guide with permanently visible setup actions and a direct link to Shizuku's official illustrated instructions.
- Adds an in-app video tutorial button that opens the shared Shizuku/Shevery setup video directly.
- Lets users export diagnostics and contact [@Glorioustr](https://t.me/Glorioustr) from the same screen.
- Shizuku theme persistence monitoring can automatically restore the last protected theme after Xiaomi replaces its active components.
- Direct **BAK → MTZ** reconstruction in Shizuku/Shevery mode; no root request and no destructive Theme Manager restore.
- Optional translation during conversion, or preservation of the original theme language.
- Converted themes are added straight to **Themes**; font-only packages remain under **Fonts**.
- Expanded multilingual XML, JSON and safe MAML translation with optional BYOK API settings and offline fallback.
- Editable Vector/LSPosed recommendations: **Android System**, **System Framework** and **Themes**.
- 20 interface languages, RTL support, Material You/Liquid Glass styles and System/Light/Dark/AMOLED modes.

## Interface

<p align="center">
  <img src="docs/screenshots/home.png" alt="HyperOS MTZ Studio home" width="30%">
  &nbsp;
  <img src="docs/screenshots/themes.png" alt="Visual theme library" width="30%">
  &nbsp;
  <img src="docs/screenshots/composer.png" alt="Theme composer" width="30%">
</p>

## Access modes

| Mode | Available workflow |
| --- | --- |
| **Root** | Private Xiaomi Themes catalog access, supported native import/apply/delete, advanced diagnostics and compatible Xposed integration. |
| **Shizuku / Shevery** | BAK Converter, authorized local import/apply, persistence monitoring and Studio tools without root. |
| **No authorization service** | Local MTZ import, preview, translation, composition and export remain available; theme applying waits for Shizuku/Shevery. |

On non-rooted devices, install and start Shizuku or [Shevery](https://github.com/HmnDev-Tech/shevery/releases) with Wireless debugging, then grant MTZ Studio permission. If either compatible manager is already installed, Studio opens that app instead of recommending another one. The in-app setup card follows the [official illustrated Shizuku guide](https://shizuku.rikka.app/guide/setup/).

## Compatibility

| Xiaomi Themes family | Status |
| --- | --- |
| `2.15.5.46`, `3.0.4.32`, `3.0.5.6` | Verified Global contract |
| `3.0.5.14` | Shizuku BAK + local apply verified; legacy direct call remains temporary/composite |
| `3.0.6.8` | Legacy tester activity removed |
| `10.8.7.6+` | Native bridge when required runtime surfaces and privileges are present |
| Other builds | Runtime activity probing is used; Shizuku imports to the library and safely opens Xiaomi Themes when direct apply is unavailable |

Modern root integration requires an active Vector/LSPosed-compatible environment and appropriate scopes. MTZ Studio recommends Android System (`android`), System Framework (`system`) and Themes (`com.android.thememanager`) while keeping the list editable.

> [!NOTE]
> Xiaomi controls the final system apply surface. Depending on the ROM and Xiaomi Themes version, a short Xiaomi activity or manual confirmation can still appear.

## Install

1. Download `MTZ_Studio_v3.4.0.apk` and its checksum from the [v3.4.0 release](https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/tag/v3.4.0).
2. Back up the Studio library before replacing an older major build.
3. Install the APK and let the app detect the available access mode.

v2.1.0 and later releases use the same stable signing key and normally support in-place upgrades. Older CI builds may require backup, uninstall and reinstall because they used a different certificate.

From v3.4.0 onward, Studio checks the official GitHub release channel automatically. A downloaded update is installed only after checksum and signing-certificate verification and Android's system confirmation.

## Build

Requirements: JDK 17, Android SDK API 36 and Android 8.0/API 26 or newer.

```powershell
.\gradlew.bat test assembleDebug
```

Release signing uses `MTZ_RELEASE_STORE_FILE`, `MTZ_RELEASE_STORE_PASSWORD`, `MTZ_RELEASE_KEY_ALIAS` and `MTZ_RELEASE_KEY_PASSWORD`.

## Documentation

- [Architecture](docs/architecture.md)
- [Theme Manager compatibility](docs/theme-manager-compatibility.md)
- [Localization](docs/localization.md)
- [Theme Language Tool](docs/theme-language-translation.md)
- [Threat model](docs/threat-model.md)

## Responsible use

Use themes, fonts, icons and artwork only when you own them or have permission from their creators. Xiaomi, HyperOS and MIUI are trademarks of their respective owners. This independent project is not affiliated with or endorsed by Xiaomi.

<p align="center">
  Built for theme makers and HyperOS enthusiasts by <a href="https://github.com/GloriousTR">GloriousTR</a>.
</p>
