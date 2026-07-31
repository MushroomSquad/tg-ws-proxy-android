# TgWsProxy for Android

Локальный MTProto WebSocket-прокси для Telegram Android **без root**, плюс встроенный **ByeDPI VPN** для обхода DPI.

```text
Other apps ──► ByeDPI VPN (tun2socks → SOCKS :1080) ──► Internet
Telegram  ──► 127.0.0.1:1443 (TgWsProxy MTProto) ──► WSS/TCP → Telegram DC
                 │
                 └─ outbound TgWsProxy sockets go through the VPN TUN
                    (ByeDPI native sockets use VpnService.protect to avoid loops)
```

Package id: `com.flowseal.tgwsproxy` (обновление поверх прежних APK).

## Возможности

1. **ByeDPI VPN** — системный трафик через локальный SOCKS ByeDPI + hev-socks5-tunnel.
2. **Telegram proxy** — локальный MTProto-прокси (Start proxy / Copy link / Open Telegram).
3. Независимые Start/Stop для VPN и proxy.
4. Полные настройки ByeDPI (UI editor + command-line).

## Установка (sideload)

1. Скачайте `TgWsProxy_android.apk` из [GitHub Releases](https://github.com/Flowseal/tg-ws-proxy/releases) (или соберите сами).
2. Разрешите установку из неизвестных источников.
3. Установите APK.

## Подключение

### ByeDPI VPN

1. Откройте **TgWsProxy** → **Start VPN**.
2. Разрешите VPN-разрешение Android.
3. При необходимости откройте **ByeDPI settings** и подберите desync-параметры.

### Telegram proxy

1. Нажмите **Start proxy**.
2. **Open in Telegram** или **Copy link**.
3. В Telegram: **Settings → Data and Storage → Proxy** — включите прокси.
4. Либо вручную:
   - Type: **MTProto**
   - Server: `127.0.0.1`
   - Port: `1443` (или ваш)
   - Secret: из приложения (`dd` + 32 hex)

Оба сервиса можно держать включёнными одновременно.

## Cloudflare fallback (список доменов)

Если прямой доступ к Telegram DC режется, прокси может уйти через Cloudflare.

- Общий список доменов лежит в репозитории: [`.github/cfproxy-domains.txt`](../.github/cfproxy-domains.txt).
- Приложение скачивает его с GitHub и кэширует на телефоне.
- На главном экране кнопка **Update domain list** обновляет кэш вручную.
- В **Proxy settings** можно задать свои CF Worker / CF proxy домены; пустые поля = общий список.
- Подробнее про схему: [CfProxy.md](./CfProxy.md), [CfWorker.md](./CfWorker.md).

## Battery / OEM

На Xiaomi, Huawei, Samsung и др. система может убивать фоновые сервисы.

1. В приложении нажмите **Battery** и разрешите игнор оптимизации.
2. В настройках телефона добавьте TgWsProxy в автозапуск / без ограничений батареи.

## Сборка из исходников

Нужны:

- JDK 17
- Android SDK (platform 35, build-tools 35)
- Android NDK (проверено с 27.0.12077973)
- CMake 3.22.1

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk
cd android
cp local.properties.example local.properties
# sdk.dir=...

# debug
./gradlew :app:assembleDebug

# release (нужен keystore — см. AndroidSigning.md)
./gradlew :app:assembleRelease
```

APK:

- debug: `app/build/outputs/apk/debug/app-debug.apk`
- release: `app/build/outputs/apk/release/app-release.apk`

Native:

- `libbyedpi.so` — CMake (`app/src/main/cpp`)
- `libhev-socks5-tunnel.so` — ndk-build (`app/src/main/jni`), задача `runNdkBuild` перед `preBuild`

Подпись, env и GitHub secrets: **[AndroidSigning.md](./AndroidSigning.md)**.

## CI (GitHub Actions)

Workflow [`.github/workflows/build.yml`](../.github/workflows/build.yml):

1. Input **Build Android APK** (`build_android`, по умолчанию включён).
2. Job: JDK 17, SDK 35, NDK `27.0.12077973`, CMake `3.22.1`, `assembleRelease`.
3. Артефакт / файл релиза: `TgWsProxy_android.apk`.
4. Полный Release публикует APK вместе с desktop-сборками.

Секреты подписи — в [AndroidSigning.md](./AndroidSigning.md).

## Автообновления в приложении

1. **CF domain list** — кнопка **Update domain list** (и автообновление при работе прокси).
2. **APK** — диалог обновления только если в GitHub Release есть файл `.apk`.

## Настройки Telegram proxy

- **DC IP** — как на desktop.
- **Use Cloudflare fallback** — запасной путь через CF-домены.
- **Own CF Worker / proxy domains** — свои домены (опционально).
- **Detailed proxy logs** — подробный лог прокси (в настройках); на главном экране логи показываются переключателем **Logs**.

## Ограничения

- Нет Fake TLS (`ee`-secret) в MTProto-прокси.
- Play Store не целевой канал.
- Always-on VPN не поддерживается (`SUPPORTS_ALWAYS_ON=false`).

## Лицензия

Android-клиент: **GPL-3.0** (из‑за кода [ByeDPIAndroid](https://github.com/dovecoteescapee/ByeDPIAndroid)).  
См. [`./LICENSE`](.././LICENSE) и [`./NOTICE`](.././NOTICE).

Desktop-часть репозитория вне `./` — по-прежнему [MIT](../LICENSE), если бинарники не смешиваются.

Vendored native:

- byedpi — MIT
- hev-socks5-tunnel — MIT
