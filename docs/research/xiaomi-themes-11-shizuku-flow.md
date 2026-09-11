# Xiaomi Themes 10.8/11 and Shizuku application flow

Date: 2026-09-11

## Conclusion

Stock Xiaomi Themes 10.9.x and 11.x packages examined for this project do not expose the old `ApplyThemeForScreenshot` activity. Shizuku supplies Android shell privileges, but it does not turn a private, missing or non-exported Xiaomi component into a callable API. The newer packages do, however, expose a supported-by-their-own-UI local detail route with an internal `REQUEST_APPLY_EVENT` hand-off that can apply an already imported local theme without coordinate-based screen automation.

The reliable capability-based flow is therefore:

1. If the installed Themes package really exposes the legacy tester activity, keep using the legacy direct route. This covers compatible old or modified packages without relying on a version list.
2. Otherwise, open Xiaomi Themes' native import flow.
3. Record the local theme identifiers before import, watch the Theme Manager metadata after returning, and identify the newly imported theme.
4. Open its exported local-resource detail URI (`ViewLocalResource://view.local.resource#<localId>`) with `REQUEST_APPLY_EVENT=true`. Xiaomi Themes then runs its own apply controller without a second user tap.

This is now the MTZ Studio strategy. It avoids showing a root requirement merely because a package belongs to the 10.8/11 family, while not claiming that Shizuku can invoke a component that the installed package does not expose.

## Evidence

### Locally inspected Xiaomi packages

The manifests and DEX files of these APKs were inspected:

- `com.android.thememanager_10.9.2.0_MemeOSUpdates.com.apk`
- `com.android.thememanager_10.9.4.0_MemeOSUpdates.com.apk`
- `com.android.thememanager_10.9.5.2_MemeOSUpdates.com.apk`
- `com.android.thememanager_11.0.8.0_MemeOSUpdates.com.apk`
- `com.android.thememanager_11.1.5.0_MemeOSUpdates.com.apk`

None declares the old `com.android.thememanager.ApplyThemeForScreenshot` route. The inspected 10.9 and 11 packages do export:

- `com.android.thememanager.mine.remote.view.activity.MineResourceTabActivity`
- `com.android.thememanager.module.detail.view.ThemeDetailActivity`

`ThemeDetailActivity` accepts the local-resource URI scheme used internally by Xiaomi:

```text
ViewLocalResource://view.local.resource#<localId>
```

The same packages' `ThemeImportManager` code constructs this URI after a successful import. `ThemeDetailActivity` maps the `REQUEST_APPLY_EVENT` intent extra to its `auto_restore` state, and `ThemeDetailFragment` invokes the theme apply controller when that state is consumed. This provides a coordinate-free one-button apply route for a known `localId`. Its import broadcasts carry Xiaomi's internal serialized resource object, so fabricating those broadcasts from MTZ Studio would still be fragile and is not treated as a supported public contract.

### Project history

Older MTZ Studio history confirms three distinct mechanisms:

- The earliest direct path launched `ApplyThemeForScreenshot` after privileged file staging.
- Later 10.8 bridge experiments depended on injected/root-side behavior and a bridge marker.
- Plain Shizuku provided the shell command channel, but did not itself recreate the missing Xiaomi activity.

This explains reports that “Shizuku used to apply directly”: the device may have had a modified Themes build exposing the legacy alias, an injected bridge/root module, or the user may have perceived import followed by Xiaomi's detail screen as one direct operation.

## Shizuku downgrade finding

On the connected HyperOS test device, `pm install -r -d` failed when Package Manager was given the downloaded `/sdcard/Download/...apk` path. SELinux denied `system_server` access to the FUSE-backed file and explicitly requested a file under `/data/local/tmp`.

The verified working sequence is:

```text
cp <downloaded-apk> /data/local/tmp/<temporary-apk>
chmod 0644 /data/local/tmp/<temporary-apk>
pm install -r -d --user 0 /data/local/tmp/<temporary-apk>
rm -f /data/local/tmp/<temporary-apk>
```

The same signed Xiaomi package successfully changed from `3.0.5.19-global` to `3.0.5.6-global` using that sequence. MTZ Studio now uses this staging path for Shizuku/Shevery downgrades and still performs package name, version, SHA-256 and signing-certificate checks before installation.

## External primary references

- [Android activity and intent-filter behavior](https://developer.android.com/guide/components/activities/intro-activities)
- [Android activity lifecycle and foreground return](https://developer.android.com/guide/components/activities/activity-lifecycle)
- [Official Shizuku introduction](https://shizuku.rikka.app/introduction/)
- [Official Shizuku setup guide](https://shizuku.rikka.app/guide/setup/)
- [Xiaomi MobileBench MIUI application launch definitions](https://github.com/XiaoMi/MobileBench/blob/main/app_list_MIUI.json)
