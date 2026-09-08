# HyperOS MTZ Studio v3.1.0

v3.1.0 unifies MTZ creation, BAK conversion, multilingual translation and adaptive Xiaomi Themes integration.

## Highlights

- **BAK Converter:** supported Xiaomi Themes backups are reconstructed as portable MTZ packages and added directly to Studio. Shizuku/Shevery mode no longer requests root or replaces Theme Manager data.
- **Optional conversion translation:** preserve the source language or translate supported visible text into the active app language.
- **Direct Shizuku apply:** Apply starts without MTZ Studio’s redundant confirmation; Xiaomi’s required system surface may still appear briefly.
- **Higher-quality translation:** reviewed Chinese terminology, multilingual XML/JSON/MAML coverage, translation memory, optional user-configured API providers and offline fallback.
- **Clean organization:** complete themes stay in Themes, while font-only resources are routed to Fonts.
- **Clear Xposed guidance:** Android System, System Framework and Themes are editable recommended targets.
- **20 interface languages** with system-language, RTL and per-app language support.
- **Rootless persistence assistance:** a no-Shizuku best-effort guard handles known ordered Xiaomi validation broadcasts; reboot recovery remains visible and user-driven rather than silently claiming success.

## Compatibility

Root, Shizuku/Shevery and standard rootless capabilities are detected at runtime. Xiaomi controls final theme acceptance and application, so behavior can differ by ROM and Xiaomi Themes build. Unknown privileged surfaces are reported as unverified rather than guessed.

v3.1.0 uses the stable signing key introduced with v2.1.0 and supports normal in-place upgrades from compatible recent releases.
