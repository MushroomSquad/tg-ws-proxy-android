# TgWsProxy Android

Локальный **MTProto ↔ WebSocket** прокси для **Telegram Android** — без root и без VPN.

Telegram ходит на `127.0.0.1` на телефоне; приложение мостит к DC Telegram по WSS/TCP (та же идея, что у desktop-версии).

```text
Telegram Android → 127.0.0.1:1443 → TgWsProxy → WSS/TCP → Telegram DC
```

**English:** [README.md](README.md)

> Package id: `com.flowseal.tgwsproxy` (legacy). Дом проекта: [MushroomSquad](https://github.com/MushroomSquad/tg-ws-proxy-android).

## Важно

- Перед установкой прочитайте **[DISCLAIMER.md](DISCLAIMER.md)**.
- **[NOTICE.md](NOTICE.md)** — порт в основном собран **вайбкодингом** с LLM.
- Кредиты upstream: **[CREDITS.md](CREDITS.md)** → [Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy) (MIT).

Мы не связаны с Telegram и Flowseal.

## Установка

### GitHub Releases

1. Откройте [Releases](https://github.com/MushroomSquad/tg-ws-proxy-android/releases).
2. Скачайте `TgWsProxy-Android-*.apk`.
3. Разрешите установку из неизвестных источников.
4. Установите APK.

### Obtainium (удобно для обновлений)

1. Установите [Obtainium](https://github.com/ImranR98/Obtainium).
2. Добавить приложение → источник **GitHub**.
3. Репозиторий: `MushroomSquad/tg-ws-proxy-android`
4. Фильтр APK: `\.apk$`
5. Берите релизы / latest по необходимости.
6. Установите и включите проверку обновлений.

Obtainium подхватит новые APK из GitHub Releases.

## Подключение Telegram

1. Откройте **TgWsProxy** → **Start** (уведомление должно висеть).
2. **Open Telegram** или **Copy link**.
3. В Telegram: **Настройки → Данные и память → Прокси** — включите прокси.
4. Вручную MTProto:
   - Сервер: `127.0.0.1`
   - Порт: `1443` (или ваш)
   - Secret: из приложения (`dd` + 32 hex)

## Батарея / OEM

Xiaomi, Huawei, Samsung и др. могут убивать сервис.

1. В приложении нажмите **Battery** и разрешите игнор оптимизации.
2. Добавьте приложение в автозапуск / без ограничений батареи.

## Подсказки по настройкам

- **DC IP**: по умолчанию DC2+DC4 → `149.154.167.220`. Если на non-Premium плохо грузятся фото/файлы — оставьте только `4:149.154.167.220` или очистите поле (см. доки upstream).
- **Refresh CF**: обновляет список CF-доменов.
- **Save logs / Share logs**: для багрепортов.

## Сборка из исходников

Нужны JDK 17 и Android SDK (API 35).

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk

./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

CI собирает подписанные APK по тегам `v*` через GitHub Actions.

## Лицензия

[MIT](LICENSE) — © Flowseal (upstream) и MushroomSquad (Android-порт).
