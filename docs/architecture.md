# Architecture and data model

## Product boundary

The product is a local MTZ library, analyzer, component picker, composer, verifier, preview foundation, and exporter. Theme Manager compatibility diagnostics and the explicit root downgrade action are isolated from MTZ parsing and composition. Failure or absence of that integration cannot disable library, composition, or export features.

## Dependency direction

`app` depends on `mtz-library`, `mtz-composer`, and `tester-adapter`. Both storage and composition depend on `mtz-core`. `mtz-core` has no Android dependency. `tester-adapter` has no dependency on the other feature modules and never receives MTZ library contents.

## Core models

- `ThemeId`: stable local UUID, never an online entitlement or Xiaomi product identifier.
- `MtzMetadata`: bounded values parsed from `description.xml`; unknown values are retained only as display metadata.
- `MtzEntry`: normalized path, compressed/uncompressed sizes, CRC, component category, and rights-file marker.
- `MtzArchive`: source path, SHA-256, metadata, entries, detected components, and safety report.
- `ThemeComponent`: category plus the archive entries that belong to it.
- `LibraryTheme`: private source path, display name, import timestamp, hash, metadata, and components.
- `ComponentSelection`: source theme and selected component category/root.
- `CompositionRequest`: output metadata, selections, and explicit conflict policy.
- `CompositionResult`: output path/hash, reopened archive, source provenance, and warnings.

## Storage

Imported sources live below `filesDir/mtz-library/<uuid>/source.mtz`. A properties manifest is written only after validation succeeds. Exports live below `filesDir/exports`. Composition records live below `filesDir/mtz-history`; they reference local source IDs and hashes, never rights content.

Source files are immutable working copies. Composition writes a temporary sibling and atomically replaces only the requested export after verification. Rights entries are reported but are never copied into a composed package.

## Conflict policy

The spike permits one selected source per component category. Duplicate normalized archive paths are rejected while parsing. If different selected components still produce the same output path, composition fails rather than silently overwriting data.
