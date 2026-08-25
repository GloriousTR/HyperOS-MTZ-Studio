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
- Startup Theme Manager compatibility diagnostics for the user-supplied Global-version behavior matrix.
- An explicit, root-authorized compatibility downgrade flow that accepts only a user-selected, package/version/signature-verified `2.15.5.46` APK.
- An isolated Theme Manager tester adapter seam.

Xposed, hooks, fabricated rights/identifiers, DRM changes, APK downloads, a theme store, and permanent theme installation are deliberately out of scope. Root is restricted to the confirmed compatibility action; it is never used to apply an MTZ or bypass a license/result check.

## Modules

| Module | Responsibility |
| --- | --- |
| `app` | Compose UI, SAF picker, orchestration, and Android share sheet |
| `mtz-core` | Safe ZIP inspection, metadata parsing, hashes, and component models |
| `mtz-library` | App-private source copies, manifests, and composition history |
| `mtz-composer` | Selection validation, conflict policy, deterministic writing, and reopen verification |
| `tester-adapter` | Theme Manager version diagnostics, verified root downgrade boundary, and optional public-intent seam; no hidden API or hook |

See [docs/architecture.md](docs/architecture.md), [docs/threat-model.md](docs/threat-model.md), [docs/spike-plan.md](docs/spike-plan.md), and [docs/theme-manager-compatibility.md](docs/theme-manager-compatibility.md).

## Build

Requirements: JDK 17 or newer and Android SDK 36.

```shell
./gradlew test assembleDebug
```

On Windows use `gradlew.bat test assembleDebug`.
