# HyperOS MTZ Studio v3.2.2

This release makes the non-root workflow predictable by requiring a Shizuku-compatible authorization service for theme application.

## Highlights

- Keeps the existing root workflow unchanged.
- Uses the current Shizuku/Shevery import, apply and persistence workflow on non-rooted devices.
- Stops launching unverified Xiaomi Themes activities when no authorization service is available.
- If a Shizuku import succeeds but that Xiaomi Themes build has no direct apply activity, opens the safe package launcher for manual selection instead of guessing an internal class.
- Opens an installed Shizuku or Shevery manager when its service is not ready.
- Recommends Shevery through its official GitHub releases page only when neither compatible manager is installed.
- Provides a numbered Wireless debugging pairing tutorial and opens Shizuku's official illustrated guide for real device screenshots.
- Adds direct Telegram support through [@Glorioustr](https://t.me/Glorioustr) beside diagnostics export and on the About screen.
- Automatically monitors and repairs a protected Shizuku theme when Xiaomi replaces the active theme components; falls back to a visible manual action if repair cannot be verified.

Local MTZ import, preview, translation, composition and export remain available before authorization. Applying a theme on a non-rooted device requires Shizuku or Shevery to be running and authorized.
