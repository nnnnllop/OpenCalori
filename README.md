# OpenCalori 🥑

**OpenCalori** — минималистичное, полностью бесплатное опенсорсное Android-приложение для подсчёта калорий и дневника питания с возможностью подключения ИИ (BYOK — *Bring Your Own Key*) для автоматического распознавания блюд по фотографии.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)]()
[![Tests](https://img.shields.io/badge/unit%20tests-184-brightgreen.svg)]()

---

## 🌟 Основные особенности

- **Конфиденциальность и локальное хранение.** Все данные (дневник питания, параметры тела, история веса) хранятся исключительно локально на устройстве.
- **Экспорт и импорт.** Полный слепок данных в читаемый JSON в один тап — чтобы дневник пережил смену телефона.
- **BYOK (Bring Your Own Key).** Поддержка любого OpenAI-совместимого API: пользователь сам выбирает провайдера, вводит API-ключ, Base URL и ID модели.
- **Трёхэтапный ИИ-сканер:**
  1. *Распознавание* — ИИ анализирует фото и формирует список продуктов.
  2. *Правка состава* — пользователь исправляет список; ИИ повторно анализирует фото с учётом правок.
  3. *Вес и КБЖУ* — оценка массы с учётом агрегатного состояния (сырой/готовый) и итоговый подсчёт.

  Любой из этапов подтверждения можно отключить в настройках.
- **Автономная база продуктов.** Встроенная локальная база (370+ позиций) с полнотекстовым поиском без интернета и ИИ, плюс свои продукты и «недавнее».
- **Минимализм и быстродействие.** Интерфейс на Jetpack Compose (Material Design 3) без рекламы, подписок и ограничений.
- **GPL-3.0.** Полностью открытый исходный код с возможностью добровольных донатов автору.

---

## 🛠 Технологический стек

| Компонент | Технология |
| :--- | :--- |
| Платформа | Android (SDK 26+) |
| Язык | Kotlin 2.0 |
| UI | Jetpack Compose, Material Design 3 |
| DI | Hilt |
| Асинхронность | Kotlin Coroutines & StateFlow |
| Локальная БД | Room (SQLite + FTS4 unicode61) |
| Безопасность | AndroidX EncryptedSharedPreferences |
| Камера | CameraX + EXIF-коррекция |
| Сетевой слой | Ktor Client (OkHttp engine) |
| Графики | Vico |
| Навигация | Navigation Compose |
| Тесты | JUnit4, kotlinx-coroutines-test, Turbine, sqlite-jdbc |

---

## 📐 Архитектура

```
com.opencalori.app/
├── data/
│   ├── backup/         # Экспорт/импорт всего дневника в JSON
│   ├── image/          # Сжатие фото, EXIF-поворот, base64
│   ├── local/          # Room DB (дневник, вес, свои продукты) + база продуктов из assets
│   ├── network/        # Ktor client, DTO, парсер ответов ИИ, тексты ошибок
│   ├── preferences/    # EncryptedSharedPreferences (API-ключи), DataStore (профиль)
│   └── repository/     # Реализации репозиториев
├── domain/
│   ├── model/          # FoodItem, Meal, UserProfile, CalorieGoal, ApiConfig
│   ├── repository/     # Интерфейсы репозиториев (границы для тестов)
│   └── usecase/        # CalculateTdeeUseCase (Mifflin-St Jeor)
├── ui/
│   ├── onboarding/     # Ввод параметров и расчёт нормы
│   ├── dashboard/      # Главный экран: прогресс, приёмы пищи, вес
│   ├── scanner/        # CameraX + трёхэтапный AI-сканер
│   ├── foodsearch/     # Поиск по базе, свои продукты, недавнее
│   ├── profile/        # Редактирование параметров тела
│   ├── settings/       # BYOK, сценарий ИИ, бэкап, донаты
│   ├── theme/          # Material 3 тема
│   ├── util/           # Форматирование чисел
│   └── components/     # Переиспользуемые Compose-компоненты
├── di/                 # Hilt-модули
└── MainActivity.kt
```

ViewModel'и зависят только от интерфейсов в `domain/repository`, поэтому весь UI-слой
покрывается юнит-тестами на JVM без эмулятора.

---

## 🚀 Сборка и запуск

### Требования

- JDK 17
- Android SDK 34 (platform + build-tools 34.0.0)
- Android Studio **или** командная строка

### Командная строка

```bash
# Клонировать репозиторий
git clone https://github.com/nnnnllop/OpenCalori.git
cd OpenCalori

# Установить sdk.dir в local.properties (или через переменную ANDROID_HOME)
echo "sdk.dir=C:\\Users\\<you>\\Android\\Sdk" > local.properties

# Сборка debug APK
./gradlew :app:assembleDebug

# Запуск unit-тестов
./gradlew :app:testDebugUnitTest
```

APK будет в `app/build/outputs/apk/debug/app-debug.apk`.

### Android Studio

Откройте папку проекта в Android Studio (Hedgehog+), дождитесь синхронизации Gradle и нажмите **Run**.

---

## 📦 Локальная база продуктов

База `app/src/main/assets/databases/products.db` генерируется скриптом:

```bash
python tools/generate_products_db.py
```

- SQLite FTS4 с токенайзером **unicode61** — без него кириллица не приводится к нижнему
  регистру и поиск строчными буквами не находит ничего.
- Префиксный поиск использует форму `слово*`: форма `"слово"*` в FTS4 ищет точное
  совпадение, а не префикс.
- Поставляется через Room `createFromAsset()`, открывается только на чтение.

> **Важно:** если меняете схему `ProductEntity`/`ProductFtsEntity`, соберите приложение,
> скопируйте новый `identity_hash` из `ProductDatabase_Impl.kt` (в `build/generated/ksp/`)
> в `ROOM_IDENTITY_HASH` скрипта и перегенерируйте базу. Тест
> `ProductsAssetDatabaseTest` упадёт, если хеши разойдутся.

---

## 🔑 Настройка BYOK

1. Откройте **Настройки** → **BYOK: подключение ИИ**.
2. Введите:
   - **Base URL** — например `https://api.openai.com/v1` или совместимый прокси.
   - **API Key** — ваш ключ.
   - **Model ID** — например `gpt-4o`.
3. Нажмите **Сохранить и проверить** — приложение выполнит текстовый и vision-тест (1×1 px PNG) для проверки мультимодальности.

Ключ хранится в `EncryptedSharedPreferences` и никогда не покидает устройство, кроме
запросов к указанному API. Из облачного бэкапа Android он исключён намеренно: мастер-ключ
живёт в Keystore и не переносится, так что восстановленный файл было бы невозможно
расшифровать.

---

## 💾 Резервные копии

**Настройки → Данные → Экспорт** сохраняет JSON со всем дневником, историей веса,
профилем и своими продуктами. **Импорт** доливает данные из файла к текущим (не стирая
то, что уже записано).

---

## 🧪 Тесты

```bash
./gradlew :app:testDebugUnitTest
```

Покрыты: расчёт TDEE и макросов, парсинг ответов ИИ (markdown-обёртки, числа строками,
обрезанный JSON), построение FTS-запросов, реальная поставляемая база продуктов через
sqlite-jdbc, форматирование числового ввода, сериализация бэкапа и все ViewModel'и
(дашборд, сканер, поиск, профиль, онбординг, настройки).

---

## 📜 Лицензия

Проект распространяется под лицензией [GNU General Public License v3.0](LICENSE).

- 100% Free & Open Source — никакой рекламы, платных функций или трекеров.
- Кнопка добровольных пожертвований находится в разделе настроек.

---

## 🤝 Участие в разработке

PR и issue приветствуются! Перед отправкой убедитесь, что:

1. `./gradlew :app:testDebugUnitTest` проходит.
2. `./gradlew :app:assembleDebug` собирается без ошибок.
3. Код соответствует стилю проекта (Kotlin official style).
