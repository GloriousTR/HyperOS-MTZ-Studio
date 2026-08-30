# HyperOS MTZ Studio v2.1.0

This release adds a separate native-library provider for Xiaomi Theme Manager `10.8.7.6` and later while preserving the v1.2.0 Global provider.

## Highlights

- Automatic provider selection based on the active Xiaomi Themes version.
- Native Theme Manager catalog, MTZ import, apply and delete operations for the modern family.
- Automatic catalog synchronization when opening or returning to the Themes screen.
- Generated MTZ files are kept in the private library and `Downloads/MTZ Studio`, then imported into Xiaomi Themes.
- Native delete no longer crashes Xiaomi Themes; the public MTZ backup remains available.
- Live Diagnostics now records startup, synchronization, composition, import, apply and delete flows, including modern bridge steps and available failure details.
- Verified modern surfaces: `10.8.7.6`, `10.9.2.0`, `10.9.4.0`, `10.9.5.2`, `11.0.8.0`, and `11.1.5.0`.

## Signing migration notice

v2.1.0 establishes the stable signing key that will be reused by future releases. Older GitHub Actions releases used an ephemeral CI debug key. Android may therefore refuse to install v2.1.0 over v2.0.0 or earlier.

Back up the MTZ Studio library first. If Android reports an incompatible signature, uninstall the older APK and install v2.1.0, then restore the backup. Releases after v2.1.0 will support normal in-place upgrades from this version.

The modern provider requires root and an enabled Vector/LSPosed-compatible Xposed scope for `com.android.thememanager`. MTZ Studio does not install Xiaomi's Theme Manager module.
