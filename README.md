# 🧩 Unik Morphe Patches 

All Type Universal Patches Available.

## ❓ About

Patches for apps I like.

<!-- TODO: Update this about section with a brief introduction/summary about this repo and what it offers. -->

### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=callmeunik/universal-patches

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.21.1](https://github.com/callmeunik/universal-patches/releases/tag/v1.21.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;58 patches total
<details open>
<summary>🌐 Universal&nbsp;&nbsp;•&nbsp;&nbsp;58 patches</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Ads Regex Advance](#ads-regex-advance) | Advanced universal ad SDK killer (load/show/init across major networks). |  |
| [Ads Regex Basic](#ads-regex-basic) | Basic universal ad killer (loadAd/showAd methods, GMS ads invokes, common ad strings). |  |
| [Bypass Emulator Detection](#bypass-emulator-detection) | Bypasses common emulator, Genymotion, BlueStacks and virtual device detection checks. |  |
| [Bypass Mandatory Login](#bypass-mandatory-login) | Bypasses common mandatory login and isLoggedIn checks. |  |
| [Bypass uninstall popup](#bypass-uninstall-popup) | Aggressively blocks uninstall-another-app popups and force-close on detect. | • Extra package names |
| [Change App Icon](#change-app-icon) | Changes the launcher icon resource name in the manifest. | • Icon resource name |
| [Change app name](#change-app-name) | Changes the app name shown on the launcher. | • App name |
| [Change package name](#change-package-name) | Changes the package name in the manifest. May break some app features. | • New package name |
| [Change version code](#change-version-code) | Changes android:versionCode. | • Version code |
| [Control app permissions](#control-app-permissions) | Removes selected permissions from the app. Turn ON only what you want to disable/remove. | • Remove INTERNET<br>• Remove CAMERA<br>• Remove RECORD_AUDIO<br>• Remove LOCATION (fine/coarse/background)<br>• Remove READ/WRITE CONTACTS<br>• Remove SMS / MMS<br>• Remove PHONE / CALL state<br>• Remove STORAGE / media files<br>• Remove CALENDAR<br>• Remove BODY_SENSORS<br>• Remove BLUETOOTH connect/scan<br>• Remove POST_NOTIFICATIONS<br>• Remove AD_ID / Ad services<br>• Remove ACTIVITY_RECOGNITION<br>• Remove REQUEST_INSTALL_PACKAGES<br>• Remove SYSTEM_ALERT_WINDOW<br>• Remove QUERY_ALL_PACKAGES |
| [Disable Analytics Ultimate](#disable-analytics-ultimate) | Disables Firebase, Adjust, AppsFlyer, Mixpanel, Amplitude, Segment, Sentry and other common trackers. |  |
| [Disable Forced Updates](#disable-forced-updates) | Bypasses common force-update and minimum-version checks. |  |
| [Disable PairIP license check](#disable-pairip-license-check) | Removes PairIP manifest components and bypasses common license, VM and background check calls. |  |
| [Disable PairIP license check (Basic)](#disable-pairip-license-check-basic) | Removes PairIP license activity, provider, service, receiver and meta-data from the manifest. Safe and size-friendly. |  |
| [Disable Play Store updates](#disable-play-store-updates) | Removes Play Store update / installer related components and permissions from the manifest. |  |
| [Disable ad SDK calls](#disable-ad-sdk-calls) | No-ops common ad SDK load/show/init/fetch methods in bundled ad packages. |  |
| [Disable clipboard access](#disable-clipboard-access) | Blocks app clipboard reads and writes. |  |
| [Disable screenshots](#disable-screenshots) | Blocks screenshots and screen recording using FLAG_SECURE on Activity onCreate. |  |
| [Disable shake ads](#disable-shake-ads) | Skips SensorManager.registerListener calls that can power shake-to-ad behavior. |  |
| [Enable Android debugging](#enable-android-debugging) | Sets android:debuggable=true. |  |
| [Enable ROM signature spoofing](#enable-rom-signature-spoofing) | Adds fake-signature permission and metadata. | • Certificate hex/signature |
| [Enable debug build target](#enable-debug-build-target) | Forces compatible BUILD_TARGET debug providers to debug=true. |  |
| [Enable screenshots](#enable-screenshots) | Removes FLAG_SECURE so screenshots and screen recording are allowed. |  |
| [Export all activities](#export-all-activities) | Makes all activities exportable. |  |
| [Export internal data documents provider](#export-internal-data-documents-provider) | Registers an extension DocumentsProvider for the app internal data directory. |  |
| [Force Allow Backup](#force-allow-backup) | Forces android:allowBackup="true" so the app can be backed up. |  |
| [Force dark theme](#force-dark-theme) | Forces common AppCompat, UiModeManager, and Configuration dark-mode checks to night mode. |  |
| [Hide ADB status](#hide-adb-status) | Hides adb_enabled and development_settings_enabled. |  |
| [Hide VPN and proxy](#hide-vpn-and-proxy) | Hides common VPN transport/interface and Java proxy property checks. |  |
| [Hide app icon](#hide-app-icon) | Removes launcher category from MAIN launcher filters. |  |
| [Hide mock location](#hide-mock-location) | Hides mock-location signals from app checks. | • Mode<br>• Provider<br>• Accuracy meters |
| [Hide root](#hide-root) | Bypasses common root, Magisk and su detection checks. |  |
| [Override certificate pinning](#override-certificate-pinning) | Forces network security config trust anchors to override pins. |  |
| [Play Integrity Bypass](#play-integrity-bypass) | Basic bypass for common Play Integrity / SafetyNet / attestation checks. |  |
| [Predictive back gesture](#predictive-back-gesture) | Enables Android predictive back gesture. |  |
| [Remove All Ads Ultimate](#remove-all-ads-ultimate) | Safe + powerful ad remover. Cleans manifest and carefully disables common ad SDK load/show/isLoaded calls. |  |
| [Remove Mod Toaster](#remove-mod-toaster) | Removes Toast/Dialog show calls and common mod APK toast messages (Telegram, t.me, Mod by). |  |
| [Remove Rate Us Popup](#remove-rate-us-popup) | Blocks common Rate Us / In-App Review dialogs and prompts. |  |
| [Remove Tracking Parameters from URLs](#remove-tracking-parameters-from-urls) | Attempts to neutralize common tracking parameters (utm_*, fbclid, gclid, mc_eid, etc.) in URLs. |  |
| [Remove ad manifest entries](#remove-ad-manifest-entries) | Removes common ad SDK permissions, services, providers, libraries, and metadata. |  |
| [Remove internet permission](#remove-internet-permission) | Removes the INTERNET permission so the app cannot access the network at all. Blocks all trackers, analytics and ads from phoning home, but also disables any legitimate online features. Only enable for apps you want fully offline. |  |
| [Remove link verification](#remove-link-verification) | Removes autoVerify from intent-filters so app links can be opened manually. |  |
| [Remove share targets](#remove-share-targets) | Removes chooser/direct share targets. |  |
| [SSL Bypass Ultimate](#ssl-bypass-ultimate) | Powerful SSL and certificate pinning bypass (OkHttp, TrustManager, Conscrypt) + trust user certs. |  |
| [Set target SDK 34](#set-target-sdk-34) | Sets targetSdkVersion to 34. |  |
| [Signature kill](#signature-kill) | Bypasses common app signature and signing verification checks. |  |
| [Spoof Android ID](#spoof-android-id) | Spoofs Settings.Secure android_id reads. | • Android ID |
| [Spoof Bluetooth identifiers](#spoof-bluetooth-identifiers) | Spoofs Bluetooth adapter name and MAC address reads. | • Bluetooth name<br>• Bluetooth MAC address |
| [Spoof Play age signals](#spoof-play-age-signals) | Spoofs Play age signal result getters. | • Lower age<br>• Upper age |
| [Spoof SIM provider](#spoof-sim-provider) | Spoofs TelephonyManager SIM/network provider values. | • Country ISO<br>• Operator code<br>• Operator name |
| [Spoof Wi-Fi connection](#spoof-wi-fi-connection) | Forces common connectivity checks to connected/unmetered. |  |
| [Spoof Wi-Fi identifiers](#spoof-wi-fi-identifiers) | Spoofs Wi-Fi SSID, BSSID, and MAC address reads. | • SSID<br>• BSSID<br>• MAC address |
| [Spoof build info](#spoof-build-info) | Spoofs common android.os.Build fields with configurable values. | • MODEL<br>• MANUFACTURER<br>• BRAND<br>• DEVICE<br>• PRODUCT<br>• FINGERPRINT<br>• VERSION.RELEASE<br>• VERSION.SDK_INT |
| [Spoof install source](#spoof-install-source) | Makes the app think it was installed from Google Play Store. |  |
| [Spoof keystore security level](#spoof-keystore-security-level) | Forces key/security level getters to software/trusted-environment style values. |  |
| [Spoof root of trust](#spoof-root-of-trust) | Spoofs common RootOfTrust verified boot getters. |  |
| [Spoof telephony IDs](#spoof-telephony-ids) | Spoofs IMEI, MEID, subscriber ID, SIM serial, and line number reads. | • IMEI<br>• MEID<br>• Subscriber ID<br>• SIM serial<br>• Line number |
| [Unlock Premium](#unlock-premium) | This patch can Unlock Premium, VIP, Pro, Gold, Subscription for Some App. |  |

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

Trigger release after removing
