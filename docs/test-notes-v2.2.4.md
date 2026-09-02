# v2.2.4 test notes

- Added the `3.0.4.32-global` Theme Manager branch to the legacy local-import contract.
- Static inspection of the supplied `3.0.2.34-global` APK confirmed the exported `ApplyThemeForScreenshot` alias and `com.android.thememanager.support3.0` action used by this branch.
- Added coverage for version detection and expanded rootsuz launch diagnostics with the resolved contract, component, launch, return and failure checkpoints.
- Legacy tester returns remain unverified: Xiaomi Themes does not provide a reliable completion callback on this protocol.
