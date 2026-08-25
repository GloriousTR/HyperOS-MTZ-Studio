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

Default limits are intentionally conservative and centralized in `MtzSecurityLimits`. They can later become advanced settings only if changes preserve safe upper bounds.

Residual risks: MTZ semantics vary between HyperOS releases; component recognition is heuristic; a structurally safe package may still be rejected by Theme Manager; previews are not yet rendered; and the public tester intent is not enabled until verified on supported devices.

