# HyperOS MTZ Studio

HyperOS MTZ Studio is an independent Android application for inspecting MTZ files owned by the user, keeping safe private working copies, and composing selected theme components into a new verifiable MTZ. It does not install themes permanently and does not bypass Xiaomi Theme Manager licensing, trial, rights, or rollback behavior.

## Spike scope

The first spike provides:

- MTZ selection through Android Storage Access Framework.
- Bounded copying into app-private storage and SHA-256 calculation.
- Safe ZIP inspection and hardened `description.xml` parsing.
- Component classification and a local theme library.
- Selection of components from two or more source themes.
- Deterministic composition, reopen verification, provenance history, and sharing.
- An isolated, disabled-by-default Theme Manager adapter seam.

Root, Xposed, hooks, fabricated rights/identifiers, DRM changes, downloads, a theme store, and permanent installation are deliberately out of scope.

## Modules

| Module | Responsibility |
| --- | --- |
| `app` | Compose UI, SAF picker, orchestration, and Android share sheet |
| `mtz-core` | Safe ZIP inspection, metadata parsing, hashes, and component models |
| `mtz-library` | App-private source copies, manifests, and composition history |
| `mtz-composer` | Selection validation, conflict policy, deterministic writing, and reopen verification |
| `tester-adapter` | Optional public-intent integration boundary; no hidden API or hook |

See [docs/architecture.md](docs/architecture.md), [docs/threat-model.md](docs/threat-model.md), and [docs/spike-plan.md](docs/spike-plan.md).

## Build

Requirements: JDK 17 or newer and Android SDK 36.

```shell
./gradlew test assembleDebug
```

On Windows use `gradlew.bat test assembleDebug`.

