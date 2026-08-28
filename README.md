# HyperOS MTZ Studio

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/logo_banner.png" alt="HyperOS MTZ Studio" width="760">
</p>

<p align="center">
  <strong>Import, organize, preview, mix and apply MTZ themes from one modern Android studio.</strong>
</p>

<p align="center">
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/GloriousTR/HyperOS-MTZ-Studio?display_name=tag&style=for-the-badge&color=7357e6"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white">
</p>

<p align="center">
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/latest"><strong>Download the latest APK</strong></a>
  ·
  <a href="docs/theme-manager-compatibility.md">Theme Manager compatibility</a>
  ·
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/issues">Report an issue</a>
</p>

---

## The MTZ workflow, rebuilt

HyperOS MTZ Studio is an independent, open-source Android application for Xiaomi HyperOS and MIUI devices. It keeps imported and generated themes in a private library, presents them with real previews, and lets you build a new MTZ by combining the parts you actually want.

<p align="center">
  <img src="docs/screenshots/home.png" alt="HyperOS MTZ Studio home screen" width="30%">
  &nbsp;
  <img src="docs/screenshots/themes.png" alt="Visual theme library" width="30%">
  &nbsp;
  <img src="docs/screenshots/composer.png" alt="MTZ theme composer" width="30%">
</p>

## What you can do

- **Import MTZ files safely.** Inspect structure and metadata before adding a theme to the app library.
- **Bring themes from Xiaomi Theme Manager.** Scan compatible device theme locations and select the themes you want to preserve as MTZ files.
- **Browse a visual theme library.** Home-screen previews, clear source labels, compact Apply actions and dedicated delete controls keep large collections manageable.
- **Create your own theme.** Start from a complete base theme and replace any of eight visual components:
  - Icons
  - Lock screen
  - Status bar
  - Dialer and contacts
  - Messages
  - Launcher
  - Always-on display
  - Font
- **Choose wallpapers independently.** Use the base theme wallpapers or select separate home-screen and lock-screen images from the gallery.
- **Preserve creator information.** Leave the creator field blank to inherit the original author, or enter your own name for the generated theme.
- **Keep generated previews.** The composer stores a persistent cover inside each generated MTZ so the library shows the theme's real home wallpaper.
- **Export and back up.** Generated themes are saved to the private library and exported to `Downloads/MTZ Studio`. The complete library can also be backed up locally or through the supported cloud destinations.
- **Diagnose difficult imports.** Live Diagnostics records the import/apply flow without crowding the main screen.

## v1.2.0 highlights

- Reworked Themes screen with visual cards, compact Apply buttons and trash actions.
- Horizontal base-theme and component selection inside the composer.
- Eight focused component categories; wallpapers are handled separately.
- Persistent preview generation for newly composed MTZ themes.
- Creator-name inheritance and optional author override.
- Consistent two-line theme titles with aligned controls.
- More polished About screen, menu cards and overall visual hierarchy.
- Global Theme Manager apply path retained for the tested 3.0.5.x family.

## Compatibility

| Xiaomi Themes version | v1.2.0 status | Notes |
| --- | --- | --- |
| **2.15.5.46** | Recommended for legacy local import | Imports MTZ as an independent local theme. Installing or downgrading a system app requires root and must be done carefully. |
| **3.0.5.x Global** | Supported | The v1.2.0 apply flow is preserved and tested on the 3.0.5.6 global build. |
| **3.0.6.8** | Limited | Xiaomi removed the legacy tester activity, so older tester-based flows are unavailable. |
| **10.8.7.6 / HyperOS Theme Manager module** | Planned for v2.0.0 | The modded Theme Manager integration will be finalized and device-tested on the dedicated v2 track. |

Some Global ROM restrictions require root plus a compatible Xposed environment such as Vector or LSPosed. HyperOS MTZ Studio only exposes these controls for themes you own or are permitted to use; it does not grant framework, root or theme rights by itself.

See [Theme Manager compatibility](docs/theme-manager-compatibility.md) for the detailed decision flow and diagnostics.

## Interface and accessibility

- Material You and Liquid Glass presentation styles.
- System, Light, Dark and AMOLED color modes.
- System-language integration with Turkish and English resources.
- Contrast-aware text and surfaces across every appearance combination.
- Settings, backup, diagnostics and About collected in a large, card-based overlay menu.

## Project structure

| Module | Responsibility |
| --- | --- |
| `app` | Jetpack Compose UI, navigation, theme previews, Xposed integration, Shevery/root coordination and export flow. |
| `mtz-core` | Hardened MTZ parsing, metadata extraction, component recognition and SHA-256 validation. |
| `mtz-library` | App-private theme storage, indexing, device scanning and backup/restore. |
| `mtz-composer` | Deterministic component composition, wallpaper handling and reopen verification. |
| `tester-adapter` | Xiaomi Theme Manager inspection, compatibility decisions and privileged runners. |

## Build from source

Requirements:

- JDK 17+
- Android SDK with API 36 installed
- Android 8.0 / API 26 or newer device target

macOS or Linux:

```shell
./gradlew test assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat test assembleDebug
```

Release APK:

```powershell
.\gradlew.bat assembleRelease
```

## Responsible use

Use themes, fonts, icons and artwork only when you own them or have permission from their creators. Xiaomi, HyperOS and MIUI are trademarks of their respective owners; this independent project is not affiliated with or endorsed by Xiaomi.

---

<p align="center">
  Built for theme makers and HyperOS enthusiasts by <a href="https://github.com/GloriousTR">GloriousTR</a>.
</p>
