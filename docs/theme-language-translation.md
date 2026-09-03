# Theme language translation

The tool rewrites the selected private MTZ in place, retaining a pre-translation source and source-history backups. A new translation uses that original source while the previous output hash still matches. An independently edited theme establishes a new baseline. Export and composition receive the committed `source.mtz` path, never the removed staging path.

## Text and expression safety

- Turkish domain/UI phrases precede on-device ML Kit translation. Other target languages do not receive the English fallback glossary unless the target is English.
- Adjacent literal fragments and literal-only `ifelse` result branches are joined before translation. This prevents `正在` + `充电` from becoming separate, unrelated words and prevents joined headings such as `VarsayılanDuvar`.
- Conditions, comparisons, package names, resource paths, original control variables and scripts are not translated.
- Display references to declarative string variables/arrays use separate localized helper variables. Event-written variables and weather-provider descriptions use display-only value mappings.
- Date output is formatted separately from translated prose. For the current-time Chinese lunar `N月e` display, numeric lunar day/month are shown with a localized label. The underlying Chinese calendar is not represented as Gregorian dates. MAML variables and date tokens are documented by [Xiaomi](https://zhuti.designer.xiaomi.com/docs/grammar/).
- XML rejects external declarations, nested archives have expansion limits, and unchanged nested packages retain their original bytes. JSON is tokenized; only known display fields change.
- Skipped files and attempted translations that still contain Han characters are included in Live Diagnostics. A successful archive rewrite is not a guarantee that every runtime state of every theme has been localized.

## Limits

Image-embedded lettering, executable scripts, unknown expression functions and arbitrary live content are not translated. Creator identities, control IDs and filenames are retained intentionally. Existing translations made before baseline support may need their original MTZ or a verified history backup to recover text that was already mistranslated.

## Verification

Run `:mtz-core:test :mtz-library:testDebugUnitTest :app:assembleDebug`. Set `MTZ_TRANSLATION_FIXTURE` to a real MTZ to exercise nested lockscreen resources. Regression cases cover charging phrases, compound headings, date arrays, weather mappings, predicate preservation, JSON escaping, archive safety and translation baselines.
