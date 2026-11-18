# KEBRIO Freedom Network (KFN)

Android VPN client — development version 0.2.7.

## Current implementation
- WireGuard backend with user-supplied configuration and Android VPN consent.
- Configuration import (up to 64 KiB), protocol detection, and encrypted local profile storage.
- Tunnel traffic counters, network diagnostics, application exclusions and optional tunnel recovery.
- Shortcut to Android VPN settings for always-on VPN and lockdown.

AmneziaWG, VLESS and Shadowsocks are detected but their connection engines are not installed. KFN does not provide VPN servers. Tunnel state alone does not prove remote handshake, internet access or leak protection.

## Build and test
Requires JDK 17, Gradle 8.9 and Android SDK 35. Run `gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon`. GitHub Actions is configured to run unit tests and upload a debug APK. Device testing and a trusted WireGuard endpoint are required to verify connectivity.

Never commit credentials, VPN configurations containing private keys, or server private keys.
