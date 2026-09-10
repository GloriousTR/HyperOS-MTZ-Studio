# HyperOS MTZ Studio v4.0.1

v4.0.1 expands Xiaomi Themes compatibility detection for modern Theme Manager modules while preserving every previously supported Global workflow.

## Changes

- Recognizes the native local MTZ library used by Xiaomi Themes 10.8.7.6 and later.
- Shows a modern Theme Manager profile as compatible only when its local-theme activity is actually available on the device.
- Keeps the runtime `ApplyThemeForScreenshot` check for supported legacy Global versions.
- Prevents unnecessary incompatibility warnings on China, EU and other ROMs using a compatible modern Theme Manager module.
- Adds the detected modern library route and final compatibility decision to Live Diagnostics.

Root, Shizuku and Shevery authorization behaviour is unchanged in this maintenance release.
