# HyperOS MTZ Studio

<p align="center">
  <strong>Modern, Powerful Theme Composer & Customization Studio for Xiaomi HyperOS & MIUI</strong>
</p>

---

## 🌟 Overview

**HyperOS MTZ Studio** is a comprehensive, standalone Android utility designed for Xiaomi HyperOS and MIUI devices. It empowers users to inspect, deconstruct, mix, and compose custom `.mtz` themes with precision, safety, and full component flexibility.

---

## ✨ Key Features

- **🎨 Temanı Oluştur (Theme Mixer):**
  - Select a **Base Theme (Temel Tema)** to automatically populate all theme components.
  - Seamlessly mix and swap individual components from different MTZ themes:
    - 📱 **Simgeler (Icons)**
    - 🔒 **Kilit Ekranı Stili (Lock Screen)**
    - 🖼️ **Duvar Kâğıdı (Wallpaper)**
    - 📶 **Durum Çubuğu (Status Bar & Notification Panel)**
    - 📞 **Arama Tuşları (Dialer & Contacts)**
    - 💬 **SMS Ekranı (SMS & Messages)**
    - 🏠 **Başlatıcı (Launcher)**
    - 🌙 **Her Zaman Açık Ekran (Always On Display / AOD)**
    - 🔤 **Yazı Tipi (Fonts)**

- **📸 Custom Gallery Wallpapers:**
  - Assign distinct high-resolution custom images directly from your Gallery for the **Home Screen** and **Lock Screen** independently during theme composition.

- **📥 Selective Device Theme Import:**
  - Scan installed themes directly from Xiaomi Themes storage (`/data/system/theme/` and storage caches).
  - Select and import only the themes you want with a clean multi-select picker dialog to save device storage.

- **💾 Public Storage Export:**
  - Composed themes are automatically exported to both the internal library and your public storage (`Downloads/MTZ Studio/<ThemeName>.mtz`) for instant sharing and manual backup.

- **🛡️ Embedded Global Theme Protection (LibXposed / Vector Hook):**
  - Built-in, lightweight LibXposed DRM validation bypass.
  - Prevents Xiaomi Theme Manager from automatically resetting 3rd-party MTZ themes back to default after 15–30 minutes or device reboots.
  - Works seamlessly with modern Xposed frameworks (Vector, LSPosed) without modifying system APKs or risking bootloops.

- **☁️ Cloud Backup & Synchronization:**
  - Backup and restore your custom MTZ library to cloud storage and Google Drive.

- **⚡ Live Diagnostics & Hardened Verification:**
  - Real-time logging of MTZ import flows.
  - Hardened XML parsing (`description.xml`), deterministic ZIP compression, SHA-256 integrity validation, and structure diagnostics.

- **💎 Modern Material 3 & Liquid Glass UI:**
  - Beautiful, responsive Compose interface supporting Dark Mode, AMOLED Black, and Liquid Glass backdrop styling.

---

## 🏗️ Architecture & Modules

The project is structured into modular Gradle subprojects:

| Module | Responsibility |
| --- | --- |
| **`app`** | Jetpack Compose UI, Navigation, LibXposed Module (`ThemeProtectionModule`), Shevery/Root IPC, SAF pickers, and Export Coordinator. |
| **`mtz-core`** | Hardened MTZ parsing, XML deserialization, cryptographic SHA-256 calculation, and component models. |
| **`mtz-library`** | App-private theme storage, metadata indexing, device theme scanner, and backup/restore engine. |
| **`mtz-composer`** | Deterministic multi-layer theme composer, asset recombination, and reopen verification. |
| **`tester-adapter`** | Xiaomi Theme Manager version inspector, compatibility heuristics, and privileged command runners (Su / Shevery). |

---

## 🛠️ Building & Requirements

- **JDK:** 17 or newer
- **Android SDK:** API 34+ (Target SDK 36, Min SDK 29)
- **Gradle:** 8.11.1+

### Build Commands

```shell
# Run unit tests
./gradlew test

# Assemble Debug APK
./gradlew assembleDebug

# Assemble Release APK
./gradlew assembleRelease
```

On Windows PowerShell:
```powershell
.\gradlew.bat test assembleDebug
```

---

## 📜 License

This project is open-source and intended for personal customization and device enhancement. All trademarks, logos, and brand names are the property of their respective owners.
