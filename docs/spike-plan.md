# Spike plan and open assumptions

## Delivery slices

1. Establish the five-module Android/Kotlin build and document product boundaries.
2. Implement safe archive inspection, XML parsing, hashes, classification, and adversarial unit tests.
3. Implement transactional private-library imports and manifest recovery.
4. Implement multi-source selection, deterministic composition, rights exclusion, reopen verification, and provenance records.
5. Add the smallest useful Compose flow: import, inspect, select, compose, verify, and share.
6. Keep Theme Manager testing behind an isolated unavailable-by-default adapter until a public intent is proven on real devices.
7. Detect the installed Global Theme Manager version at startup and isolate the user-confirmed root compatibility downgrade from all MTZ operations.

## Acceptance mapping

SAF and private copying are owned by `app`/`mtz-library`; safety, metadata, component listing, and hashes by `mtz-core`; multi-theme composition and reopen verification by `mtz-composer`; export by Android Sharesheet; tester research by `tester-adapter`.

## Explicit assumptions

- Minimum Android version is API 26 so private file operations can use modern Java NIO safely.
- An MTZ is ZIP-compatible and a recognized component is either a top-level entry (for example `icons`) or a directory subtree (for example `wallpaper/...`).
- The spike selects at most one source for each component category. Finer per-resource merging is deferred.
- `description.xml` output contains locally authored descriptive metadata, not product IDs, online IDs, entitlements, or rights.
- Rights-like paths are matched conservatively and excluded from composition. False positives are preferable to copying protected material.
- Preview rendering and detailed HyperOS-version compatibility matrices are follow-up work; this spike lists structure and validation findings.
- A composed package is structurally verifiable, not guaranteed installable or accepted by Xiaomi Theme Manager.
- The version behavior matrix is product input and applies only to the listed Global versions; unknown versions remain explicitly unverified.
- Root is permitted only for installing a user-supplied, verified `2.15.5.46` Theme Manager APK with Package Manager's replace/downgrade operation.

