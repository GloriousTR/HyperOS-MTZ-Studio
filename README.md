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
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/tag/v3.0.0"><strong>Download v3.0.0</strong></a>
  ·
  <a href="docs/release-notes-v3.0.0.md">Release notes</a>
  ·
  <a href="docs/theme-manager-compatibility.md">Compatibility details</a>
  ·
  <a href="https://github.com/GloriousTR/HyperOS-MTZ-Studio/issues">Report an issue</a>
</p>

---

## One studio, adaptive access modes

HyperOS MTZ Studio is an independent, open-source Android application for Xiaomi HyperOS and MIUI devices. The app detects both the active `com.android.thememanager` family and the privileges actually available—there is no separate rooted, rootless, Global or modern APK.

| Provider | Theme Manager family | Library behavior | Apply and remove behavior |
| --- | --- | --- | --- |
| **Global** | Verified on `2.15.5.46` and `3.0.5.6` | Imported and generated MTZ files remain in MTZ Studio's private library | Uses the preserved version-specific Global contract |
| **Modern with root** | `10.8.7.6+` when the verified runtime surface is present | Choose individual Theme Manager themes by default; opening the entire catalog is an explicit, guarded action | Uses Theme Manager's native import, apply and delete operations |
| **Shizuku / Shevery** | Any detected Theme Manager family | Keeps the complete Studio workspace and uses only capabilities actually granted by the service | Uses supported public/system-shell hand-off paths; it never presents shell access as root |
| **Rootless workspace** | Any detected Theme Manager family | Imported and generated MTZ files remain in MTZ Studio's private library | Exports to `Downloads/MTZ Studio` and hands the user to Xiaomi Themes for any supported manual import |

The provider decision follows the **active installed Theme Manager APK and verified UID 0 access**, not the presence of a root-module directory. Unknown versions are reported as unverified instead of being guessed.

### Rootless mode

Root is not required to import, inspect, preview, organize, personalize, compose, export, back up or restore local MTZ files. On a rootless device, the app keeps its local workspace visible even when a modern Xiaomi Themes version is installed. **Apply** retains the MTZ under `Downloads/MTZ Studio`. Verified legacy `2.15.5.46`/`3.0.5.6` builds receive the same exported tester request with that public path, but its return is explicitly reported as unverified. Other builds use an explicit manual hand-off: Studio tries a public file-open surface exposed by Xiaomi Themes and otherwise opens its local/main screen for user-driven import. The app does not report that a theme was applied merely because Xiaomi Themes opened.

Root remains necessary for reading Xiaomi Themes' private catalog and active font, automatic native import/apply/delete, Theme Manager downgrade, Xposed scope preparation and permanent Global theme protection. ADB-mode Shizuku is intentionally treated as rootless because UID 2000 cannot read Xiaomi's private theme data.

## v3.0.0 highlights

- **BAK Import:** rooted devices can select a Xiaomi Themes `.bak` archive and let Studio validate, restore and index its themes. The restored items appear in Studio's Themes library without forcing a jump to Xiaomi Themes.
- **Three adaptive access modes:** Studio selects root, Shizuku/Shevery or rootless behavior from verified runtime access. The first screen no longer briefly reports rootless mode while privilege discovery is still running.
- **Theme Language Tool:** translate the selected theme in place while retaining its original source for clean retranslation. Nested MTZ components, XML/JSON display text and safe MAML text expressions are covered.
- **Natural Turkish theme text:** charging, weather, date and customization terminology use a domain glossary and post-processing instead of raw word-for-word output. Code, predicates and MAML control expressions are preserved.
- **Cleaner theme actions:** each card uses a single-line ellipsized title, a full-width Apply action and a compact second row for Translate and Delete.
- **Traceable operations:** BAK restore, localization, composition, import and apply steps report progress and failures through Live Diagnostics.

## v2.3.0 foundations

- **Safe modern catalog access:** modern Xiaomi Themes libraries are no longer mirrored automatically at app launch. **Import a theme from Theme Manager** is the default, lightweight route.
- **Guarded full-library view:** **Show all themes in Theme Manager** counts the native library first. Libraries over 24 entries require explicit confirmation before their MTZ sources are reconstructed.
- **Background-safe work:** a confirmed large catalog is processed in resumable groups while Studio keeps its own import, compose and apply workflows responsive. Foreground MTZ operations pause catalog work and it resumes afterward.
- **APK-assisted compatibility diagnostics:** rooted users can explicitly export the installed Xiaomi Themes base APK. Package identity, installed version and SHA-256 are verified before the Android share sheet opens; nothing is uploaded automatically.
- **Runtime capability record:** Live Diagnostics records the detected Theme Manager branch, legacy tester availability and split-package count to support new ROM compatibility profiles.

### Earlier v2.2.0 highlights

- **20 interface languages:** the app follows the system language and offers Android 13+ per-app language selection. Simplified and Traditional Chinese are separate options, and Arabic, Persian and Urdu use RTL layout.
- **Complete personalization:** all eight supported sections remain visible. Category-specific screenshots take priority over generic covers, and every matching category preview can be viewed without cropping.
- **System-default sources:** a theme with a category preview but no actual component can retain its name and preview while the generated MTZ uses the system default for that component.
- **Safer root and catalog handling:** root channels are verified before privileged reads, stale records never delete private sources, and loading, empty and error states are reported separately.
- **Automatic translation checks:** every build verifies all 317 strings across all bundled locale variants, including their formatting parameters.

- **Modern catalog with root:** selected themes can be reconstructed from Xiaomi Theme Manager; v2.3.0 makes full-library reconstruction an explicit guarded action.
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

<p align="center"><sub>The Themes screenshot shows the Global provider. On modern Theme Manager builds, users can import individual themes first or explicitly open the guarded full-library flow.</sub></p>

- Material You and Liquid Glass presentation styles.
- System, Light, Dark and AMOLED color modes.
- **20 interface languages:** English, Turkish, Brazilian Portuguese, Spanish, Chinese, Russian, Indonesian, Arabic, German, French, Hindi, Bengali, Urdu, Japanese, Vietnamese, Marathi, Telugu, Tamil, Persian and Korean. Simplified and Traditional Chinese are separate Android locale choices. The app follows the system language, supports RTL navigation and declares per-app languages on Android 13+. New translations are machine-assisted and community language review is welcome; see [language coverage and review notes](docs/localization.md).
- Contrast-aware text and surfaces across every appearance combination.
- Settings, backup, diagnostics and About collected in a large card-based overlay menu.

## Theme workflow

### Import and library

- Inspect MTZ structure and metadata before adding a file.
- Keep imported and generated themes in the app-private library.
- Browse themes through real home-screen previews with a full-width Apply action plus compact Translate and Delete controls.
- Restore supported Xiaomi Themes `.bak` archives on rooted devices through the dedicated **BAK Import** card.
- Translate supported visible theme text in place with the **Theme Language Tool**; the original archive is retained internally so changing language does not compound an earlier machine translation.
- Export generated MTZ files to `Downloads/MTZ Studio` and create a restorable library backup.
- On compatible rooted modern builds, import selected Xiaomi Theme Manager themes or explicitly request a guarded full-library reconstruction.
- Without root, retain the complete local editor and hand exported MTZ files to any public import surface Xiaomi Themes exposes.

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

On modern builds, **Import a theme from Theme Manager** is the default route. **Show all themes in Theme Manager** first reads the metadata count and asks for confirmation when there are more than 24 entries, because a large native library can be expensive to reconstruct on some ROMs. A full-library pass never deletes private MTZ files just because a native record disappeared; explicit theme deletion remains separate.

### Diagnose every step

Live Diagnostics keeps a bounded private journal and displays the latest 200 events. Modern bridge requests return intermediate steps over an authenticated callback channel, while available host failures are attached to the relevant operation. Exported logs redact URI values. On rooted devices, users can explicitly export the installed Xiaomi Themes base APK for compatibility analysis; package identity, version and SHA-256 are verified first, and the APK is never uploaded automatically. A Global legacy activity return is explicitly marked **unverified** because returning from that activity alone does not prove that Xiaomi applied the theme.

## Compatibility and requirements

| Xiaomi Themes version | v3.0.0 status | Notes |
| --- | --- | --- |
| `2.15.5.46` | Supported legacy path | Imports an MTZ as an independent local theme; this is the recommended legacy version. |
| `3.0.5.6` | Supported Global path | Uses the device-verified legacy tester contract. |
| `3.0.5.14` | Version-specific behavior | Xiaomi interprets the tester request as a temporary/composite application over Default. |
| `3.0.6.8` | Limited | Xiaomi removed the legacy tester activity. |
| `10.8.7.6+` modern family | Supported when runtime checks pass | Uses the native catalog, import, apply and delete bridge. |
| Unknown versions | Unverified | The library and composer remain available; unsupported privileged operations are not guessed. |

The automatic modern provider requires:

1. Root access.
2. An enabled Vector or LSPosed-compatible Xposed environment.
3. Runtime scope approval for `com.android.thememanager` only. The APK does not publish a fixed
   Xposed scope list, so Vector/LSPosed will not lock the scope screen with a “static scope” notice.

Without these requirements the app automatically selects its rootless local workspace; MTZ creation and library features remain available.

Global Theme Protection and the redundant `system` scope are hidden on modern builds because the modern Theme Manager/module path already provides its own theme persistence behavior. Legacy Global builds request `system` and `com.android.thememanager` at runtime only when Global Theme Protection is enabled. MTZ Studio does not install Xiaomi Theme Manager modules, silently change store-update settings or modify root-module mounts.

For the complete decision flow, tested surfaces and downgrade safety boundary, see [Theme Manager compatibility](docs/theme-manager-compatibility.md).

## Install or upgrade

1. Download `MTZ_Studio_v3.0.0.apk` and its `.sha256` file from the [v3.0.0 release](https://github.com/GloriousTR/HyperOS-MTZ-Studio/releases/tag/v3.0.0).
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
- [Theme Language Tool design and safety](docs/theme-language-translation.md)
- [v3.0.0 release notes](docs/release-notes-v3.0.0.md)

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
