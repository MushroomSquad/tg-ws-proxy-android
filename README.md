# TgWsProxy Android — Telegram MTProto WebSocket Proxy (no root)

[![Release](https://img.shields.io/github/v/release/MushroomSquad/tg-ws-proxy-android?include_prereleases)](https://github.com/MushroomSquad/tg-ws-proxy-android/releases/latest)
[![APK](https://img.shields.io/badge/download-APK-blue)](https://github.com/MushroomSquad/tg-ws-proxy-android/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Obtainium](https://img.shields.io/badge/updates-Obtainium-green)](https://github.com/ImranR98/Obtainium)

**Keywords:** Telegram Android proxy · MTProto · WebSocket · local proxy · no root · no VPN · Obtainium APK · Kotlin Compose

Local **MTProto ↔ WebSocket** proxy for **Telegram Android** — no root, no VPN.

Telegram talks to `127.0.0.1` on your phone; this app bridges to Telegram DCs over WSS/TCP (same idea as the desktop [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy)).

```text
Telegram Android → 127.0.0.1:1443 → TgWsProxy → WSS/TCP → Telegram DC
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

1. Open **TgWsProxy** → **Start** (keep the notification).
2. **Open Telegram** or **Copy link**.
3. In Telegram: **Settings → Data and Storage → Proxy** — enable the proxy.
4. Manual MTProto:
   - Server: `127.0.0.1`
   - Port: `1443` (or your setting)
   - Secret: from the app (`dd` + 32 hex)

## Battery / OEM

Xiaomi, Huawei, Samsung, etc. may kill the foreground service.

1. Tap **Battery** in the app and allow ignoring battery optimizations.
2. Add the app to OEM autostart / unrestricted battery lists.

## Settings tips

- **DC IP**: default maps DC2+DC4 to `149.154.167.220` (fronting). If photos/files fail on non-Premium, try only `4:149.154.167.220` or clear the field (see upstream docs).
- **Refresh CF**: refreshes Cloudflare proxy domain list (may still use upstream raw lists).
- **Save logs / Share logs**: for bug reports.

## Build from source

Needs JDK 17 and Android SDK (API 35).

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk

# Optional local signing (or use env vars — see CI)
# place keystore/tgwsproxy.jks and set:
# KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD

./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

CI builds signed APKs on version tags (`v*`) via GitHub Actions.

## Related searches

telegram proxy android · mtproto websocket · tg ws proxy · local mtproto proxy · obtainium telegram · android no root proxy

## License

[MIT](LICENSE) — © Flowseal (upstream) and MushroomSquad (Android port).
