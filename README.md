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
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/tag/v3.2.0"><strong>Download v3.2.0</strong></a>
  · <a href="docs/release-notes-v3.2.0.md">Release notes</a>
  · <a href="docs/theme-manager-compatibility.md">Compatibility</a>
  · <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/issues">Report an issue</a>
</p>

## What it does

- **MTZ library:** safely imports, validates, previews and organizes complete themes, icons and fonts.
- **BAK Converter:** converts supported Xiaomi Themes `.bak` archives directly into portable MTZ packages without replacing Xiaomi Themes data. Results appear immediately in the Studio library.
- **Theme Language Tool:** detects visible theme text and translates it into the app language. Reviewed Chinese terminology, safe MAML handling, translation memory and optional user-configured API providers improve natural results.
- **Theme Composer:** combines a base theme with selected icons, lock screen, status bar, dialer, messages, launcher, AOD, font and wallpapers while preserving untouched components.
- **Adaptive application:** chooses the safest available Root, Shizuku/Shevery or standard rootless route for the installed Xiaomi Themes family.
- **Live Diagnostics:** records import, conversion, translation, composition and apply stages for compatibility analysis.

## v3.2.0 highlights

- Direct **BAK → MTZ** reconstruction in Shizuku/Shevery mode; no root request and no destructive Theme Manager restore.
- Optional translation during conversion, or preservation of the original theme language.
- Converted themes are added straight to **Themes**; font-only packages remain under **Fonts**.
- Standard rootless and Shizuku apply now start without MTZ Studio’s redundant second confirmation.
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
| **Shizuku / Shevery** | BAK Converter, Studio workspace and supported public/system-shell hand-offs without presenting shell access as root. |
| **Standard rootless** | MTZ import, preview, translation, composition, export and supported Xiaomi Themes manual/public hand-off. |

Standard rootless mode also runs a best-effort persistence guard against known ordered Xiaomi validation broadcasts. It does not require Shizuku, but it cannot silently write private Theme Manager data or guarantee persistence on every ROM. After a reboot, Studio can offer a visible one-tap return to the last selected theme instead of applying it invisibly.

## Compatibility

| Xiaomi Themes family | Status |
| --- | --- |
| `2.15.5.46`, `3.0.4.32`, `3.0.5.6` | Verified Global contract |
| `3.0.5.14` | Xiaomi temporary/composite behavior |
| `3.0.6.8` | Legacy tester activity removed |
| `10.8.7.6+` | Native bridge when required runtime surfaces and privileges are present |
| Other builds | Studio tools remain available; privileged behavior is not guessed |

Modern root integration requires an active Vector/LSPosed-compatible environment and appropriate scopes. MTZ Studio recommends Android System (`android`), System Framework (`system`) and Themes (`com.android.thememanager`) while keeping the list editable.

> [!NOTE]
> Xiaomi controls the final system apply surface. Depending on the ROM and Xiaomi Themes version, a short Xiaomi activity or manual confirmation can still appear.

## Install

1. Download `MTZ_Studio_v3.2.0.apk` and its checksum from the [v3.2.0 release](https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/tag/v3.2.0).
2. Back up the Studio library before replacing an older major build.
3. Install the APK and let the app detect the available access mode.

v2.1.0 and later releases use the same stable signing key and normally support in-place upgrades. Older CI builds may require backup, uninstall and reinstall because they used a different certificate.

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
