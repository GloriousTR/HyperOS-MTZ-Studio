# HyperOS MTZ Studio v3.0.0

Version 3.0.0 brings the BAK restore workflow, adaptive root/Shizuku/rootless access and a substantially safer Theme Language Tool into one stable release.

## What's new

- **BAK Import for rooted devices:** select a Xiaomi Themes `.bak` archive and let Studio validate, restore and index its contents. Complete themes are added to Themes, while font-only resources are routed exclusively to Fonts without an unnecessary redirect to Xiaomi Themes.
- **Adaptive privilege detection:** the opening screen waits for capability discovery and then selects verified root, Shizuku/Shevery or rootless behavior. Shell access is not mistaken for UID 0.
- **Theme Language Tool:** translates the selected theme in place and keeps an internal original for clean retranslation to another language.
- **Deeper theme coverage:** nested MTZ packages, XML and JSON display resources, safe MAML text expressions, date patterns, weather text and customization labels are handled.
- **Natural Turkish output:** a theme-specific glossary and translation post-processing correct awkward literal phrases such as charging and customization labels while leaving program logic untouched.
- **Improved theme cards:** names stay on one line with ellipsis; Apply gets a full row, while Translate and Delete share the compact row below.
- **Better diagnostics:** BAK restore and localization report progress, changed files, skipped resources and unresolved text to Live Diagnostics.

## Access modes

- **Root:** full supported restore, private catalog, native apply/delete and privileged diagnostic operations.
- **Shizuku or Shevery:** supported shell/public operations only; it does not impersonate root.
- **Rootless:** import, inspect, preview, compose, translate, export, backup and manual Xiaomi Themes hand-off remain available.

## Compatibility

The existing Global provider paths and the runtime-verified modern `10.8.7.6+` bridge are preserved. Unsupported Xiaomi Themes surfaces are reported as unverified instead of being guessed.

Theme localization changes text resources only. Text embedded in raster images, proprietary encrypted resources or arbitrary executable scripts cannot be translated safely and is left unchanged.

## Upgrade

v3.0.0 uses the stable signing key introduced with v2.1.0, so supported stable releases can update in place. Backing up the Studio library before any major upgrade is still recommended.
