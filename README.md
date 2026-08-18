# 👋🧩 Morphe Patches template

Template repository for Morphe Patches.

## ❓ About

Patches for apps I like.

<!-- TODO: Update this about section with a brief introduction/summary about this repo and what it offers. -->

### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=callmeunik/universal-patches

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.4.0](https://github.com/callmeunik/universal-patches/releases/tag/v1.4.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;34 patches total
<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;34 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Change version code](#change-version-code) | Changes android:versionCode. | • Version code |
| [Disable PairIP license check](#disable-pairip-license-check) | Removes PairIP manifest components and bypasses common license, VM and background check calls. |  |
| [Disable Play Store updates](#disable-play-store-updates) | Removes Play Store update / installer related components and permissions from the manifest. |  |
| [Disable ad SDK calls](#disable-ad-sdk-calls) | No-ops common ad SDK load/show/init/fetch methods in bundled ad packages. |  |
| [Disable clipboard access](#disable-clipboard-access) | Blocks app clipboard reads and writes. |  |
| [Disable shake ads](#disable-shake-ads) | Skips SensorManager.registerListener calls that can power shake-to-ad behavior. |  |
| [Enable Android debugging](#enable-android-debugging) | Sets android:debuggable=true. |  |
| [Enable ROM signature spoofing](#enable-rom-signature-spoofing) | Adds fake-signature permission and metadata. | • Certificate hex/signature |
| [Enable debug build target](#enable-debug-build-target) | Forces compatible BUILD_TARGET debug providers to debug=true. |  |
| [Export all activities](#export-all-activities) | Makes all activities exportable. |  |
| [Export internal data documents provider](#export-internal-data-documents-provider) | Registers an extension DocumentsProvider for the app internal data directory. |  |
| [Force dark theme](#force-dark-theme) | Forces common AppCompat, UiModeManager, and Configuration dark-mode checks to night mode. |  |
| [Hide ADB status](#hide-adb-status) | Hides adb_enabled and development_settings_enabled. |  |
| [Hide VPN and proxy](#hide-vpn-and-proxy) | Hides common VPN transport/interface and Java proxy property checks. |  |
| [Hide app icon](#hide-app-icon) | Removes launcher category from MAIN launcher filters. |  |
| [Hide mock location](#hide-mock-location) | Hides mock-location signals from app checks. | • Mode<br>• Provider<br>• Accuracy meters |
| [Override certificate pinning](#override-certificate-pinning) | Forces network security config trust anchors to override pins. |  |
| [Predictive back gesture](#predictive-back-gesture) | Enables Android predictive back gesture. |  |
| [Remove ad manifest entries](#remove-ad-manifest-entries) | Removes common ad SDK permissions, services, providers, libraries, and metadata. |  |
| [Remove all ads](#remove-all-ads) | Removes common ad SDK activities, services, receivers, providers, permissions and meta-data from the manifest. |  |
| [Remove all ads (powerful)](#remove-all-ads-powerful) | Removes ad components from manifest and disables common ad SDK load/show/isLoaded calls. |  |
| [Remove internet permission](#remove-internet-permission) | Removes the INTERNET permission so the app cannot access the network at all. Blocks all trackers, analytics and ads from phoning home, but also disables any legitimate online features. Only enable for apps you want fully offline. |  |
| [Remove share targets](#remove-share-targets) | Removes chooser/direct share targets. |  |
| [Set target SDK 34](#set-target-sdk-34) | Sets targetSdkVersion to 34. |  |
| [Spoof Android ID](#spoof-android-id) | Spoofs Settings.Secure android_id reads. | • Android ID |
| [Spoof Bluetooth identifiers](#spoof-bluetooth-identifiers) | Spoofs Bluetooth adapter name and MAC address reads. | • Bluetooth name<br>• Bluetooth MAC address |
| [Spoof Play age signals](#spoof-play-age-signals) | Spoofs Play age signal result getters. | • Lower age<br>• Upper age |
| [Spoof SIM provider](#spoof-sim-provider) | Spoofs TelephonyManager SIM/network provider values. | • Country ISO<br>• Operator code<br>• Operator name |
| [Spoof Wi-Fi connection](#spoof-wi-fi-connection) | Forces common connectivity checks to connected/unmetered. |  |
| [Spoof Wi-Fi identifiers](#spoof-wi-fi-identifiers) | Spoofs Wi-Fi SSID, BSSID, and MAC address reads. | • SSID<br>• BSSID<br>• MAC address |
| [Spoof build info](#spoof-build-info) | Spoofs common android.os.Build fields with configurable values. | • MODEL<br>• MANUFACTURER<br>• BRAND<br>• DEVICE<br>• PRODUCT<br>• FINGERPRINT<br>• VERSION.RELEASE<br>• VERSION.SDK_INT |
| [Spoof keystore security level](#spoof-keystore-security-level) | Forces key/security level getters to software/trusted-environment style values. |  |
| [Spoof root of trust](#spoof-root-of-trust) | Spoofs common RootOfTrust verified boot getters. |  |
| [Spoof telephony IDs](#spoof-telephony-ids) | Spoofs IMEI, MEID, subscriber ID, SIM serial, and line number reads. | • IMEI<br>• MEID<br>• Subscriber ID<br>• SIM serial<br>• Line number |

</details>

<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

UserXYZ Patches are licensed under the [GNU General Public License v3.0](LICENSE)

Trigger release after removing bad import
