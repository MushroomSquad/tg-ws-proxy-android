# Android: подпись APK и секреты CI

Как подписывать Android-сборку локально и в GitHub Actions.

## Зачем

Release APK должен быть подписан одним и тем же keystore, иначе Android не даст обновить приложение поверх уже установленного.

Package id: `com.flowseal.tgwsproxy`.

## Локальная подпись

### Вариант A — файл keystore (проще для разработки)

1. Создать keystore (не коммитить):

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/tgwsproxy.jks \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -alias tgwsproxy \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=TgWsProxy, OU=Mobile, O=Flowseal, C=RU"
```

2. Gradle по умолчанию ищет `keystore/tgwsproxy.jks`.

3. Собрать:

```bash
cd android
./gradlew :app:assembleRelease
```

`keystore/` уже в `.gitignore`. Не кладите `.jks` / `.keystore` в git.

Для локальной sideload-сборки в `android/app/build.gradle.kts` есть fallback-пароли `tgwsproxy`, если env не заданы и файл keystore существует. Для публичного релиза замените на свои и не храните пароли в репозитории.

### Вариант B — через переменные окружения

```bash
export KEYSTORE_FILE=/absolute/path/to/release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
cd android
./gradlew :app:assembleRelease
```

Читается в [`android/app/build.gradle.kts`](../android/app/build.gradle.kts) (`signingConfigs.release`).

## GitHub Actions secrets

Workflow: [`.github/workflows/build.yml`](../.github/workflows/build.yml) → job `build-android`.

Settings репозитория → **Secrets and variables** → **Actions** → добавить:

| Secret | Обязателен | Описание |
|--------|------------|----------|
| `ANDROID_KEYSTORE_BASE64` | да* | Содержимое `.jks`/`.keystore`, закодированное в base64 |
| `KEYSTORE_PASSWORD` | да* | Пароль store |
| `KEY_ALIAS` | да* | Alias ключа |
| `KEY_PASSWORD` | да* | Пароль ключа |

\*Если `ANDROID_KEYSTORE_BASE64` не задан, CI создаёт **ephemeral** keystore. Сборка проходит, артефакт `TgWsProxy_android.apk` появляется, но подпись не совпадёт с вашей локальной/прод-подписью.

### Как положить keystore в secret

```bash
base64 -w0 keystore/tgwsproxy.jks > keystore.b64
# macOS:
# base64 -i keystore/tgwsproxy.jks -o keystore.b64
```

Содержимое `keystore.b64` целиком вставьте в secret `ANDROID_KEYSTORE_BASE64` (одна строка, без переносов предпочтительнее).

Проверка decode локально:

```bash
base64 -d keystore.b64 > /tmp/check.jks
keytool -list -keystore /tmp/check.jks
```

## Артефакты

| Где | Имя файла |
|-----|-----------|
| Локальный release | `android/app/build/outputs/apk/release/app-release.apk` |
| CI artifact / GitHub Release | `TgWsProxy_android.apk` |

В полном Release (все платформы + `make_release`) APK публикуется вместе с desktop-сборками.

## Чего не делать

- Не коммитить keystore, пароли, `.b64` с ключом.
- Не ротировать prod-keystore без понимания: пользователи с уже установленным APK не смогут обновиться поверх.
- Не путать ephemeral CI-подпись с продовой.

## Связанные документы

- [README.android.md](./README.android.md) — установка, ByeDPI, сборка
- [BuildFromSource.md](./BuildFromSource.md) — desktop + Android
- [CONTRIBUTING.md](./CONTRIBUTING.md)
