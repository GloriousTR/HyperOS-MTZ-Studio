# HyperOS MTZ Studio v2.2.0

v2.2.0 turns the post-v2.1.0 device fixes into a public release and makes MTZ Studio accessible to a much wider international community.

## Highlights

- **20 interface languages:** English, Turkish, Brazilian Portuguese, Spanish, Chinese, Russian, Indonesian, Arabic, German, French, Hindi, Bengali, Urdu, Japanese, Vietnamese, Marathi, Telugu, Tamil, Persian and Korean. Simplified and Traditional Chinese are supplied separately, giving Android 21 locale choices.
- **System-language integration:** the interface follows the device language by default. Android 13+ can also select an app-specific language in system settings.
- **RTL-ready interface:** Arabic, Persian and Urdu receive right-to-left layout and automatically mirrored navigation icons.
- **Translation integrity checks:** all 317 app strings are checked on every build for missing keys, empty values and changed formatting parameters. New translations are machine-assisted drafts with reviewed navigation and safety wording; community language review remains welcome.
- **Complete personalization grid:** all eight supported component sections remain visible. If a theme has a category preview but no corresponding component package, MTZ Studio can keep that source name and preview while using the system default for the actual component.
- **More accurate previews:** category-specific images take priority over generic theme covers, all matching category previews can be viewed without cropping, and truly absent categories display a clear selection prompt.
- **Safer composition:** choosing the system default removes the base theme's custom package for that category, including the status-bar companion package where required. Source labels survive reopening a generated theme.
- **More reliable catalog access:** private root channels are verified before use, Shizuku/Shevery authorization failures are explained, stale native records do not delete private sources, and empty metadata folders no longer break catalog reads.
- **Clear catalog states:** loading, empty catalog, partial read and access errors are shown separately. Built-in ROM themes remain available through the Xiaomi Themes shortcut instead of being presented as editable MTZ sources.
- **Responsive settings menu:** settings cards retain the compact 80 dp minimum but grow for translated text and large font settings.

## Compatibility

The Global workflows verified on Xiaomi Themes `2.15.5.46` and `3.0.5.6` remain isolated from the modern provider. Theme Manager `10.8.7.6+` uses the native catalog bridge only when its required runtime surfaces are present. Unknown versions remain unverified instead of being guessed.

The modern provider requires root plus an enabled Vector/LSPosed-compatible Xposed scope for `com.android.thememanager`. MTZ Studio does not install a Xiaomi Theme Manager module, change system-app update settings or modify root-module mounts.

## Upgrade and signing

v2.2.0 uses the stable release certificate introduced with v2.1.0, so it supports a normal in-place update from v2.1.0 without removing app data.

Releases through v2.0.0 used an ephemeral CI debug certificate. Android may reject a direct update from those releases. Back up the Studio library, uninstall the old APK only if Android reports a signature conflict, install v2.2.0, then restore the backup.

Full details: [README](../README.md), [language coverage](localization.md), and [Theme Manager compatibility](theme-manager-compatibility.md).
