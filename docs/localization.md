# Localization — 20 languages

This localization set ships with HyperOS MTZ Studio v2.2.0.

The interface follows the device language by default, with English as the fallback. Android 13+ also exposes the supported languages in the system's per-app language settings. There is no online translation dependency in the APK.

## Coverage

English, Turkish, Brazilian Portuguese, Spanish, Chinese (Simplified and Traditional), Russian, Indonesian, Arabic, German, French, Hindi, Bengali, Urdu, Japanese, Vietnamese, Marathi, Telugu, Tamil, Persian and Korean.

This is a practical selection of 20 widely used languages, including our Brazilian and Chinese communities—not a claim of an exact worldwide speaker ranking. Chinese has two script variants, so Android lists 21 locale options. Portuguese resources use Brazilian wording and also serve as the fallback for other Portuguese locales. Simplified Chinese is the Chinese fallback; Traditional Chinese uses the `Hant` script qualifier. Indonesian uses Android's legacy `values-in` resource qualifier and the modern `id` locale identifier.

## Translation quality

- Every app string resource has an entry in every supported locale.
- New translations are machine-assisted drafts with reviewed navigation, component names and common actions. Brazilian Portuguese and Simplified Chinese received additional contextual review.
- Native-speaker review is still needed before claiming professional linguistic validation in all 20 languages.
- Theme names, creator names, imported previews and technical details reported by Android/root/Theme Manager are not automatically translated. Exported diagnostic events and some low-level error details retain their original language for troubleshooting.
- Arabic, Persian and Urdu use Android/Compose RTL layout. Directional navigation icons mirror automatically. Settings cards retain an 80 dp minimum and can grow for longer text or larger fonts.

## Maintaining translations

Edit `app/src/main/res/values/strings.xml` first, then update the same key in every translated `strings.xml`. Preserve parameter types and positions such as `%1$s`, `%2$d`, XML escaping and technical package names. Never translate the command `pm install -r -d`, file paths, URLs or product names.

Run `./gradlew :app:verifyTranslations :app:lintDebug test`. Every app build also runs the resource check automatically. It rejects missing/extra keys, duplicates, empty values, changed formatting arguments and leftover draft markers. Locale declarations in `res/xml/locales_config.xml` must match the bundled resources. The check verifies structural integrity, not linguistic accuracy.

For language feedback, include the language, screen, current phrase and suggested replacement. Test at least home, Settings, Appearance, Themes, component selection, confirmation dialogs and About at both normal and enlarged font sizes. Do not apply or remove a theme just to test translations.

## Draft tooling provenance

Initial drafts were generated locally with Meta's [NLLB-200 distilled 600M](https://huggingface.co/facebook/nllb-200-distilled-600M), using an INT8 conversion and CTranslate2, then revised. The model is CC-BY-NC-4.0 and was used only as a local draft aid for this non-commercial project. Neither model weights nor the translation runtime are distributed in the APK or required to build the app. Build-time checks use only the JDK.

Android language integration follows the [official per-app language documentation](https://developer.android.com/guide/topics/resources/app-languages).
