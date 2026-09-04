# Theme language translation

The tool rewrites the selected private MTZ in place, retaining a pre-translation source and source-history backups. A new translation uses that original source while the previous output hash still matches. An independently edited theme establishes a new baseline. Export and composition receive the committed `source.mtz` path, never the removed staging path.

## Text and expression safety

- The source language is detected on-device for each visible text fragment. Translation targets the active MTZ Studio app language, including all 20 bundled interface languages.
- Supported Chinese phrases still pass through the reviewed domain glossary before on-device ML Kit translation. This preserves the established Chinese-to-Turkish quality for charging, weather, date and customization terminology instead of replacing it with a generic translation path.
- Hiragana/Katakana and Hangul are recognized before general language identification so Japanese and Korean theme text is not mistaken for Chinese merely because it also contains Han characters.
- Translation models are downloaded only for source/target pairs actually encountered in the selected theme and are closed when the operation finishes.
- Adjacent literal fragments and literal-only `ifelse` result branches are joined before translation. This prevents `正在` + `充电` from becoming separate, unrelated words and prevents joined headings such as `VarsayılanDuvar`.
- Conditions, comparisons, package names, resource paths, original control variables and scripts are not translated.
- Display references to declarative string variables/arrays use separate localized helper variables. Event-written variables and weather-provider descriptions use display-only value mappings.
- Date output is formatted separately from translated prose. For the current-time Chinese lunar `N月e` display, numeric lunar day/month are shown with a localized label. The underlying Chinese calendar is not represented as Gregorian dates. MAML variables and date tokens are documented by [Xiaomi](https://zhuti.designer.xiaomi.com/docs/grammar/).
- XML rejects external declarations, nested archives have expansion limits, and unchanged nested packages retain their original bytes. JSON is tokenized; only known display fields change.
- Skipped files, detected source languages and text whose language could not be determined are included in Live Diagnostics. A successful archive rewrite is not a guarantee that every runtime state of every theme has been localized.

## Limits

Image-embedded lettering, executable scripts, unknown expression functions and arbitrary live content are not translated. Creator identities, control IDs and filenames are retained intentionally. Automatic language identification covers more languages than the on-device translation engine; unsupported or uncertain fragments are therefore preserved rather than guessed. Existing translations made before baseline support may need their original MTZ or a verified history backup to recover text that was already mistranslated.

## Verification

Run `:mtz-core:test :mtz-library:testDebugUnitTest :app:assembleDebug`. Set `MTZ_TRANSLATION_FIXTURE` to a real MTZ to exercise nested lockscreen resources. Regression cases cover Chinese charging phrases, multilingual runtime text, compound headings, date arrays, weather mappings, predicate preservation, JSON escaping, archive safety and translation baselines.
