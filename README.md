# TgWsProxy Android — Telegram MTProto WebSocket Proxy + optional ByeDPI VPN

[![Release](https://img.shields.io/github/v/release/MushroomSquad/tg-ws-proxy-android?include_prereleases)](https://github.com/MushroomSquad/tg-ws-proxy-android/releases/latest)
[![APK](https://img.shields.io/badge/download-APK-blue)](https://github.com/MushroomSquad/tg-ws-proxy-android/releases/latest)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Obtainium](https://img.shields.io/badge/updates-Obtainium-green)](https://github.com/ImranR98/Obtainium)

**Keywords:** Telegram Android proxy · MTProto · WebSocket · local proxy · ByeDPI · VPN · Obtainium APK · Kotlin Compose

Local **MTProto ↔ WebSocket** proxy for **Telegram Android**, plus an optional **ByeDPI** system VPN. Proxy and VPN start/stop independently.

Telegram talks to `127.0.0.1` on your phone; this app bridges to Telegram DCs over WSS/TCP (same idea as the desktop [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy)).

```text
Telegram Android → 127.0.0.1:1443 → TgWsProxy → WSS/TCP → Telegram DC
Optional: ByeDPI VpnService for DPI bypass on device traffic
```

**Русская версия:** [README.ru.md](README.ru.md)

> Package id: `com.flowseal.tgwsproxy` (legacy). Project home: [MushroomSquad/tg-ws-proxy-android](https://github.com/MushroomSquad/tg-ws-proxy-android).

## Important

- Read **[DISCLAIMER.md](DISCLAIMER.md)** before installing.
- Read **[NOTICE.md](NOTICE.md)** — this port was largely **vibe-coded** with LLM assistants.
- Credits to upstream desktop project: **[CREDITS.md](CREDITS.md)** → [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy) (MIT).

We are not affiliated with Telegram or Flowseal.

## Install

### GitHub Releases

1. Open [Releases](https://github.com/MushroomSquad/tg-ws-proxy-android/releases).
2. Download `TgWsProxy-Android-*.apk`.
3. Allow install from unknown sources for your file manager / browser.
4. Install the APK.

### Obtainium (recommended for updates)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Add app → source **GitHub**.
3. Repository: `MushroomSquad/tg-ws-proxy-android`
4. Include filter (APK): `\.apk$`
5. Prefer releases / latest release as needed.
6. Install / enable update checks.

Obtainium will pick new APKs from GitHub Releases automatically.

## Connect Telegram

1. Open **TgWsProxy** → start the **Telegram proxy** (keep the notification).
2. Optionally start **ByeDPI VPN** (system VPN permission) and open ByeDPI settings from the app.
3. **Open Telegram** or **Copy link**.
4. In Telegram: **Settings → Data and Storage → Proxy** — enable the proxy.
5. Manual MTProto:
   - Server: `127.0.0.1`
   - Port: `1443` (or your setting)
   - Secret: from the app (`dd` + 32 hex)

## Battery / OEM

Xiaomi, Huawei, Samsung, etc. may kill the foreground service.

1. Tap **Battery** in the app and allow ignoring battery optimizations.
2. Add the app to OEM autostart / unrestricted battery lists.

## Settings tips

- **DC IP**: default maps DC2+DC4 to `149.154.167.220` (fronting). If photos/files fail on non-Premium, try only `4:149.154.167.220` or clear the field (see upstream docs).
- **Update domain list**: refreshes Cloudflare proxy domain list.
- **ByeDPI settings**: full ByeDPI preference screens (desync, hosts, etc.).
- **Save logs / Share logs**: for bug reports.

## Build from source

Needs JDK 17, Android SDK (API 35), NDK, and CMake.

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk

# Optional local signing — see docs/AndroidSigning.md
# place keystore/tgwsproxy.jks and set:
# KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD

./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

CI builds signed APKs on version tags (`v*`) via GitHub Actions.

## Related searches

telegram proxy android · mtproto websocket · tg ws proxy · byedpi android · local mtproto proxy · obtainium telegram

## License

[GPL-3.0](LICENSE) for this app (includes ByeDPIAndroid-derived code). Upstream desktop protocol: MIT ([Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy)). See [NOTICE.md](NOTICE.md) and [CREDITS.md](CREDITS.md).
