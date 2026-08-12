# OpenCalori 🥑

**OpenCalori** — минималистичное, полностью бесплатное опенсорсное Android-приложение для подсчёта калорий и дневника питания с возможностью подключения ИИ (BYOK — *Bring Your Own Key*) для автоматического распознавания блюд по фотографии.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)]()

---

## 🌟 Основные особенности

- **Конфиденциальность и локальное хранение.** Все данные (дневник питания, параметры тела, история веса) хранятся исключительно локально на устройстве.
- **BYOK (Bring Your Own Key).** Поддержка любого OpenAI-совместимого API: пользователь сам выбирает провайдера, вводит API-ключ, Base URL и ID модели.
- **Двухэтапная проверка ИИ (AI Verification Loop):**
  1. *Распознавание* — ИИ анализирует фото и формирует первоначальный список продуктов.
  2. *Корректировка и расчёт* — пользователь проверяет и исправляет список; ИИ повторно анализирует фото с учётом правок, оценивает массу и КБЖУ (учитывая агрегатное состояние: сырой/варёный продукт).
- **Автономная база продуктов (<30 МБ).** Встроенная компактная локальная база данных для ручного поиска и ввода продуктов без интернета и ИИ.
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
| Локальная БД | Room (SQLite) |
| Безопасность | AndroidX EncryptedSharedPreferences |
| Камера | CameraX |
| Сетевой слой | Ktor Client (OkHttp engine) |
| Графики | Vico |
| Навигация | Navigation Compose |

---

## 📐 Архитектура

```
com.opencalori.app/
├── data/
│   ├── local/          # Room DB (дневник, вес) + предсобранная база продуктов
│   ├── network/        # Ktor client, DTO для OpenAI-совместимого API
│   ├── preferences/    # EncryptedSharedPreferences (API-ключи), DataStore (профиль)
│   └── repository/     # MealRepository, ProductRepository, AiRepository
├── domain/
│   ├── model/          # FoodItem, Meal, UserProfile, CalorieGoal, ApiConfig
│   └── usecase/        # CalculateTdeeUseCase (Mifflin-St Jeor)
├── ui/
│   ├── onboarding/     # Ввод параметров и расчёт нормы
│   ├── dashboard/      # Главный экран: прогресс, приёмы пищи, вес
│   ├── scanner/        # CameraX + двухэтапный AI-сканер
│   ├── foodsearch/     # Поиск по локальной базе продуктов
│   ├── settings/       # Настройки BYOK, профиль, донаты
│   ├── theme/          # Material 3 тема
│   └── components/     # Переиспользуемые Compose-компоненты
├── di/                 # Hilt-модули
└── MainActivity.kt
```

---

## 🚀 Сборка и запуск

### Требования

- JDK 17
- Android SDK 34 (platform + build-tools 34.0.0)
- Android Studio **или** командная строка

### Командная строка

```bash
# Клонировать репозиторий
git clone https://github.com/<your-username>/OpenCalori.git
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

- Используется SQLite FTS4 для полнотекстового поиска.
- Поставляется через Room `createFromAsset()`.
- Размер — десятки КБ, легко расширяется.

> **Важно:** если меняете схему `ProductEntity`/`ProductFtsEntity`, пересоберите приложение, скопируйте новый `identity_hash` из `ProductDatabase_Impl.kt` (в `build/generated/ksp/`) в `ROOM_IDENTITY_HASH` скрипта и перегенерируйте базу.

---

## 🔑 Настройка BYOK

1. Откройте **Настройки** → **BYOK: подключение ИИ**.
2. Введите:
   - **Base URL** — например `https://api.openai.com/v1` или совместимый прокси.
   - **API Key** — ваш ключ.
   - **Model ID** — например `gpt-4o`.
3. Нажмите **Сохранить и проверить** — приложение выполнит текстовый и vision-тест (1×1 px PNG) для проверки мультимодальности.

Ключ хранится в `EncryptedSharedPreferences` и никогда не покидает устройство, кроме запросов к указанному API.

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
