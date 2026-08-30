# HyperOS MTZ Studio

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/logo_banner.png" alt="HyperOS MTZ Studio" width="760">
</p>

<p align="center">
  <strong>Import, organize, preview, compose and apply MTZ themes from one modern Android studio.</strong>
</p>

<p align="center">
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/GloriousTR/HyperOS-MTZ-Studio?display_name=tag&style=for-the-badge&color=7357e6"></a>
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/actions/workflows/release.yml"><img alt="Release workflow" src="https://img.shields.io/github/actions/workflow/status/GloriousTR/HyperOS-MTZ-Studio/release.yml?style=for-the-badge&label=Release"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white">
</p>

<p align="center">
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/tag/v2.2.0"><strong>Download v2.2.0</strong></a>
  ·
  <a href="docs/release-notes-v2.2.0.md">Release notes</a>
  ·
  <a href="docs/theme-manager-compatibility.md">Compatibility details</a>
  ·
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/issues">Report an issue</a>
</p>

---

## One studio, two Theme Manager providers

HyperOS MTZ Studio is an independent, open-source Android application for Xiaomi HyperOS and MIUI devices. Version 2.2.0 detects the active `com.android.thememanager` version and selects the appropriate provider automatically—there is no separate Global or modern APK.

| Provider | Theme Manager family | Library behavior | Apply and remove behavior |
| --- | --- | --- | --- |
| **Global** | Verified on `2.15.5.46` and `3.0.5.6` | Imported and generated MTZ files remain in MTZ Studio's private library | Uses the preserved version-specific Global contract |
| **Modern** | `10.8.7.6+` when the verified runtime surface is present | Xiaomi Theme Manager's local catalog is the source of truth and synchronizes automatically | Uses Theme Manager's native import, apply and delete operations |

The provider decision follows the **active installed Theme Manager APK**, not the presence of a root-module directory. Unknown versions are reported as unverified instead of being guessed.

## v2.2.0 highlights

- **20 interface languages:** the app follows the system language and offers Android 13+ per-app language selection. Simplified and Traditional Chinese are separate options, and Arabic, Persian and Urdu use RTL layout.
- **Complete personalization:** all eight supported sections remain visible. Category-specific screenshots take priority over generic covers, and every matching category preview can be viewed without cropping.
- **System-default sources:** a theme with a category preview but no actual component can retain its name and preview while the generated MTZ uses the system default for that component.
- **Safer root and catalog handling:** root channels are verified before privileged reads, stale records never delete private sources, and loading, empty and error states are reported separately.
- **Automatic translation checks:** every build verifies all 317 strings across all bundled locale variants, including their formatting parameters.

- **Automatic modern catalog:** opening or returning to Themes refreshes the Xiaomi Theme Manager catalog. The old manual import card and refresh button are hidden on modern builds.
- **Native modern operations:** themes added or composed in Studio are retained in `Downloads/MTZ Studio`, imported into Xiaomi Themes and associated with the returned local resource.
- **Safer removal:** deleting a modern catalog item removes its native Theme Manager record and editor mirror without deleting the public MTZ backup.
- **Global behavior preserved:** the tested `2.15.5.46` and `3.0.5.6` workflows remain isolated from the modern bridge.
- **Detailed Live Diagnostics:** startup, provider selection, synchronization, composition, import, apply and delete operations are recorded from app launch—not only while the diagnostics screen is open.
- **Stable release signing:** v2.2.0 reuses the v2.1.0 stable signing key for normal in-place upgrades.

Modern host surfaces were inspected in `10.8.7.6`, `10.9.2.0`, `10.9.4.0`, `10.9.5.2`, `11.0.8.0` and `11.1.5.0`. A future numeric version is accepted only if the required runtime classes are also available.

## Interface

<p align="center">
  <img src="docs/screenshots/home.png" alt="HyperOS MTZ Studio home screen" width="30%">
  &nbsp;
  <img src="docs/screenshots/themes.png" alt="Visual theme library" width="30%">
  &nbsp;
  <img src="docs/screenshots/composer.png" alt="MTZ theme composer" width="30%">
</p>

<p align="center"><sub>The Themes screenshot shows the Global provider. On modern Theme Manager builds the manual import panel is removed and the native catalog is synchronized automatically.</sub></p>

- Material You and Liquid Glass presentation styles.
- System, Light, Dark and AMOLED color modes.
- **20 interface languages:** English, Turkish, Brazilian Portuguese, Spanish, Chinese, Russian, Indonesian, Arabic, German, French, Hindi, Bengali, Urdu, Japanese, Vietnamese, Marathi, Telugu, Tamil, Persian and Korean. Simplified and Traditional Chinese are separate Android locale choices. The app follows the system language, supports RTL navigation and declares per-app languages on Android 13+. New translations are machine-assisted and community language review is welcome; see [language coverage and review notes](docs/localization.md).
- Contrast-aware text and surfaces across every appearance combination.
- Settings, backup, diagnostics and About collected in a large card-based overlay menu.

## Theme workflow

### Import and library

- Inspect MTZ structure and metadata before adding a file.
- Keep imported and generated themes in the app-private library.
- Browse themes through real home-screen previews with compact Apply and delete controls.
- Export generated MTZ files to `Downloads/MTZ Studio` and create a restorable library backup.
- On compatible modern builds, work directly with Xiaomi Theme Manager's synchronized local catalog.

### Compose a theme

Choose a complete base theme, then replace only the parts you want:

- Icons
- Lock screen
- Status bar
- Dialer and contacts
- Messages
- Launcher
- Always-on display
- Font

Home-screen and lock-screen wallpapers can be selected independently. Unchanged components remain inherited from the base theme. Generated themes preserve a reusable preview, and the creator field can either inherit the original author or use a custom name.

Category-specific images (including language-prefixed filenames) take priority over generic covers. A source with previews but no component can be selected as **System default will be used**. Composition excludes the base theme's custom component for that category while retaining the selected source name and all category previews in the MTZ. Studio restores that source label when the generated theme is used as a base again. This label does not override Xiaomi's own component labels; the preview is not a rendering of the system default. The preview viewer shows every category image without cropping. If neither a component nor a specific preview exists, the category stays empty with **Choose a theme**.

On modern builds, the synchronized catalog covers readable local theme records. ROM-bundled themes can live outside that catalog; the bottom **Open built-in themes in Xiaomi Themes** action opens their native library. These built-in items are not advertised as editable Studio sources. A refresh never deletes private MTZ files just because a native record disappeared; explicit theme deletion remains separate.

### Diagnose every step

Live Diagnostics keeps a bounded private journal and displays the latest 200 events. Modern bridge requests return intermediate steps over an authenticated callback channel, while available host failures are attached to the relevant operation. Exported logs redact URI values. A Global legacy activity return is explicitly marked **unverified** because returning from that activity alone does not prove that Xiaomi applied the theme.

## Compatibility and requirements

| Xiaomi Themes version | v2.2.0 status | Notes |
| --- | --- | --- |
| `2.15.5.46` | Supported legacy path | Imports an MTZ as an independent local theme; this is the recommended legacy version. |
| `3.0.5.6` | Supported Global path | Uses the device-verified legacy tester contract. |
| `3.0.5.14` | Version-specific behavior | Xiaomi interprets the tester request as a temporary/composite application over Default. |
| `3.0.6.8` | Limited | Xiaomi removed the legacy tester activity. |
| `10.8.7.6+` modern family | Supported when runtime checks pass | Uses the native catalog, import, apply and delete bridge. |
| Unknown versions | Unverified | The library and composer remain available; unsupported privileged operations are not guessed. |

The modern provider requires:

1. Root access.
2. An enabled Vector or LSPosed-compatible Xposed environment.
3. Scope approval for `com.android.thememanager` only.

Global Theme Protection and the redundant `system` scope are hidden on modern builds because the modern Theme Manager/module path already provides its own theme persistence behavior. MTZ Studio does not install Xiaomi Theme Manager modules, silently change store-update settings or modify root-module mounts.

For the complete decision flow, tested surfaces and downgrade safety boundary, see [Theme Manager compatibility](docs/theme-manager-compatibility.md).

## Install or upgrade

1. Download `MTZ_Studio_v2.2.0.apk` and its `.sha256` file from the [v2.2.0 release](https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/tag/v2.2.0).
2. Back up the Studio library before replacing an older installation.
3. Install the APK, then allow the app to detect the active Xiaomi Themes provider.
4. On modern builds, approve the `com.android.thememanager` Xposed scope and reboot if your framework requires it.

> [!IMPORTANT]
> Releases up to v2.0.0 were produced with an ephemeral CI debug certificate. Android may therefore reject an in-place update to v2.2.0. If you see a signature conflict, back up the Studio library, uninstall the old APK, install v2.2.0 and restore the backup. v2.1.0 and later releases use the same stable signing key, so v2.1.0 can update normally.

> [!NOTE]
> HyperOS MTZ Studio does not bundle or download Xiaomi APKs. The optional root downgrade action accepts only a user-selected, correctly signed `2.15.5.46` APK after package, version, certificate and SHA-256 verification.

## Project structure

| Module | Responsibility |
| --- | --- |
| `app` | Jetpack Compose UI, navigation, previews, provider selection, Xposed integration, privileged coordination and export flow |
| `mtz-core` | Hardened MTZ parsing, metadata extraction, component recognition and SHA-256 validation |
| `mtz-library` | App-private theme storage, indexing, device scanning and backup/restore |
| `mtz-composer` | Deterministic component composition, wallpaper handling and reopen verification |
| `tester-adapter` | Xiaomi Theme Manager inspection, compatibility decisions and privileged runners |

Additional technical documentation:

- [Architecture](docs/architecture.md)
- [Theme Manager compatibility](docs/theme-manager-compatibility.md)
- [Threat model](docs/threat-model.md)
- [Localization and language review](docs/localization.md)
- [v2.2.0 release notes](docs/release-notes-v2.2.0.md)

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

Release builds intentionally require a private signing configuration supplied through these environment variables:

- `MTZ_RELEASE_STORE_FILE`
- `MTZ_RELEASE_STORE_PASSWORD`
- `MTZ_RELEASE_KEY_ALIAS`
- `MTZ_RELEASE_KEY_PASSWORD`

With that configuration present:

```powershell
.\gradlew.bat assembleRelease
```

## Responsible use

Use themes, fonts, icons and artwork only when you own them or have permission from their creators. Xiaomi, HyperOS and MIUI are trademarks of their respective owners. This independent project is not affiliated with or endorsed by Xiaomi.

---

<p align="center">
  Built for theme makers and HyperOS enthusiasts by <a href="https://github.com/GloriousTR">GloriousTR</a>.
</p>
