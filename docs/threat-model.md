# MTZ threat model

MTZ input is untrusted even when selected by the user.

| Threat | Spike control |
| --- | --- |
| Path traversal / Zip Slip | Normalize separators; reject absolute, drive-prefixed, empty, `.` and `..` segments before any read or write |
| Duplicate-name ambiguity | Reject duplicate normalized paths case-insensitively |
| Zip bomb | Bound source bytes, entry count, per-entry expanded bytes, total expanded bytes, metadata bytes, and compression ratio |
| Truncated/corrupt ZIP | Fully stream every non-directory entry and verify the observed byte count; reopen composed output |
| Symlink entry | Inspect ZIP central-directory Unix mode and reject symbolic links |
| XML entity expansion / XXE | Disable DOCTYPE, external entities, XInclude, and external DTD/schema access; bound XML bytes |
| Rights/DRM misuse | Report rights paths; never alter, synthesize, or copy them to composed packages |
| Output collision | Fail closed on overlapping paths; never use last-entry-wins |
| Source mutation | Copy to private storage; treat validated source as read-only |
| Partial output | Write to a temporary file, reopen/verify, then atomically move |
| Lost provenance | Persist source IDs, SHA-256 hashes, selected categories/roots, and output SHA-256 |
| Temporary data leakage | Keep temporary files inside app-private directories and delete them on success or failure |
| Malicious Theme Manager APK | Require user selection; validate package name, exact canonical version, installed-package signing certificate match, bounded size, and SHA-256 before root |
| Root command injection | Pass only a fixed command and numeric byte count to `su`; stream APK bytes over stdin; never interpolate URI, filename, package name, or user text |
| Unsafe system mutation | Use only Package Manager replacement/downgrade flags after a second confirmation; never write `/system`, uninstall the package, disable verification, or alter app data |
| Silent downgrade | Show installed/target versions, package and APK hash; require risk acknowledgement and a final confirmation; report Package Manager output |

Default limits are intentionally conservative and centralized in `MtzSecurityLimits`. They can later become advanced settings only if changes preserve safe upper bounds.

Residual risks: MTZ semantics vary between HyperOS releases; component recognition is heuristic; a structurally safe package may still be rejected by Theme Manager; previews are not yet rendered; the public tester intent is not enabled until verified on supported devices; and release Android builds may reject `pm install -d` even with a root shell. A failed downgrade is reported and does not trigger a more invasive fallback.
