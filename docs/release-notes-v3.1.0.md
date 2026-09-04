# HyperOS MTZ Studio v3.1.0

Version 3.1.0 expands the Theme Language Tool beyond Chinese while preserving the reviewed Chinese translation quality and makes Vector/LSPosed setup easier to understand.

## What's new

- **Automatic source-language detection:** visible theme text is detected fragment by fragment and translated into the active MTZ Studio app language using supported on-device models.
- **Chinese quality preserved:** the existing reviewed glossary and theme-specific rules still run before generic translation, retaining natural charging, weather, date and customization terminology.
- **Multilingual MAML support:** safe display text written at runtime can be translated without changing predicates, variables, resource paths or executable behavior.
- **Safe fallback:** uncertain or unsupported text is preserved instead of guessed. Detected languages and skipped fragments are recorded in Live Diagnostics.
- **Editable recommended Xposed targets:** Vector/LSPosed now identifies Android System, System Framework and Themes as the app's recommended scopes. No fixed libxposed scope list is used, so the selection remains editable.
- **Localized guidance:** the Theme Language Tool explanation is updated across all 20 bundled interface languages.

## Existing features retained

BAK Import, adaptive root/Shizuku/rootless access, MTZ composition, font-only package separation, modern `10.8.7.6+` Theme Manager integration and the verified Global workflows remain available without changing their established behavior.

## Notes

Translation models are downloaded on first use for the source/target language pairs found in the theme. Images containing text, encrypted proprietary resources, unknown scripts and code expressions are intentionally left unchanged.

Automatic Xiaomi Themes catalog access, BAK restore and native import/apply/delete still require verified root access. Xposed scope approval and root permission are separate requirements.

## Upgrade

v3.1.0 uses the stable signing key introduced with v2.1.0 and can update supported recent releases in place. Backing up the Studio library before a major upgrade is still recommended.
