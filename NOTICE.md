# TgWsProxy Android — notices

This Android app was largely vibe-coded with LLM assistants. Review critically before relying on it.

## Upstream desktop protocol

- Project: https://github.com/Flowseal/tg-ws-proxy
- License: MIT
- Used for: MTProto ↔ WebSocket proxy logic (ported to Kotlin)

## ByeDPIAndroid

- Project: https://github.com/dovecoteescapee/ByeDPIAndroid
- License: GNU General Public License v3.0
- Used for: VPN service glue, preference screens, JNI bindings patterns

Because GPL-3.0 ByeDPIAndroid code is included, this combined Android application
(`com.flowseal.tgwsproxy`) is distributed under GPL-3.0. See `LICENSE`.

## byedpi

- Project: https://github.com/hufrea/byedpi
- License: MIT
- Path: `app/src/main/cpp/byedpi/`

## hev-socks5-tunnel

- Project: https://github.com/heiher/hev-socks5-tunnel
- License: MIT
- Path: `app/src/main/jni/hev-socks5-tunnel/`

## Docs

- `docs/README.android.md`
- `docs/AndroidSigning.md` (keystore + GitHub Actions secrets)
