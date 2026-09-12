# HyperOS MTZ Studio Privacy Policy

Last updated: September 10, 2026

HyperOS MTZ Studio is an open-source Android application for importing, inspecting, translating, composing, backing up, and applying Xiaomi MTZ themes.

## Data processed by the app

- Theme and backup files are processed on the user's device unless the user explicitly starts a cloud backup, cloud restore, or online translation.
- When Google Drive backup is connected, the app requests the user's primary Google Account email address to identify the selected account and uses the Google Drive application-data permission (`drive.appdata`) to create, read, replace, and delete only HyperOS MTZ Studio's own hidden backup files. The app cannot browse or modify the user's ordinary Google Drive files with this permission.
- Google OAuth access tokens are used only for the requested operation and are not stored by HyperOS MTZ Studio.
- When the user configures an online translation provider and starts translation, theme text required for that operation is sent to the provider selected by the user. Provider credentials are stored using Android's encrypted credential storage and are not included in diagnostic reports.
- Diagnostic reports are created locally and are shared only when the user explicitly exports or sends them.

## Data sharing and sale

HyperOS MTZ Studio does not sell personal data, serve advertisements, or share Google user data with third parties. Data is sent only to services deliberately selected by the user for backup, restore, or translation.

## Retention and deletion

Local app data can be removed through Android settings or by uninstalling the app. Google Drive backups can be replaced or removed from within the app; disconnecting Google Drive removes the local connection state. Users may also revoke the app's Google Account access from their Google Account security settings.

## Security

The app requests the minimum Google Drive scope needed for portable backups. Its source code is publicly available for inspection. No system can guarantee absolute security, but the project avoids persisting OAuth access tokens and verifies backup archives before restoring them.

## Contact

Questions or privacy requests can be sent to the developer through Telegram: [@Glorioustr](https://t.me/Glorioustr).

Project source: [GloriousApps/HyperOS-MTZ-Studio](https://github.com/GloriousApps/HyperOS-MTZ-Studio)
