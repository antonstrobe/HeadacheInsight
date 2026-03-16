# HeadacheInsight Product Prompt

Репозиторная Markdown-версия исходного рабочего промпта для проекта `HeadacheInsight`.

## Роли

- Senior Android architect
- Senior Kotlin / Jetpack Compose engineer
- Mobile UX designer для low-stimulation health apps
- Secure backend engineer
- QA / test automation engineer
- DevOps bootstrap engineer

## Продуктовая цель

Построить Android-приложение для фиксации и анализа головной боли и мигрени со следующими свойствами:

- ultra-fast capture за 1-2 действия;
- voice-first ввод во время приступа;
- offline-first ядро с локальным хранилищем как primary source of truth;
- optional cloud analysis только через безопасный backend proxy;
- clinician-friendly exports и накопление истории;
- безопасный язык без диагностики и без претензии на medical device.

## Ключевые ограничения

- Нативный Android-клиент на Kotlin и Jetpack Compose.
- Строгая multi-module архитектура с разделением `ui / domain / data`.
- OpenAI API key может храниться локально в зашифрованном изолированном хранилище Android по решению пользователя.
- Любые cloud-запросы идут только через backend.
- Локальный red-flag safety engine обязан работать без интернета.
- Основной UX не строится вокруг чата.
- Нет рекламы, сторонних трекеров и хранения chain-of-thought.

## UX и безопасность

- Большая primary-кнопка `У меня болит голова`.
- Два режима: `Acute Quick Mode` и `Deep Review Mode`.
- Partial save / resume.
- Видимый offline/cloud status.
- Urgent escalation screen при red flags.
- Безопасные формулировки: приложение не ставит диагноз и не заменяет врача.

## Архитектурные ориентиры

- Android references: `Now in Android`, `android/architecture-templates`.
- Pattern references: `Headi`, `life-notes`.
- Local ASR references: `sherpa-onnx`, `whisper.cpp`, `WhisperKitAndroid`.
- Использовать references как ориентиры, а не как источник слепого копирования.

## Технический стек

### Android

- Kotlin
- Jetpack Compose
- Navigation Compose
- ViewModel + UDF/MVI-like state management
- Coroutines + Flow
- Room
- DataStore
- WorkManager
- Hilt
- Kotlin serialization

### Backend

- FastAPI
- Pydantic
- SQLAlchemy
- Official OpenAI SDK
- Dockerfile
- `.env.example`

## Обязательные функциональные зоны

- onboarding + privacy/disclaimer
- home
- quick log
- episode detail
- questionnaire
- profile
- attachments
- insights
- reports
- settings
- sync queue
- backend proxy
- tests и CI

## Delivery Phases

1. Repo bootstrap
2. Core app foundation
3. Quick log + local storage
4. Local speech pipeline
5. Backend + cloud analysis
6. Dynamic questions + attachments
7. Insights + reports
8. Polish + tests + CI

## Связанные документы

- [README.md](README.md)
- [docs/implementation-plan.md](docs/implementation-plan.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/architecture-decisions.md](docs/architecture-decisions.md)
- [docs/privacy-and-safety.md](docs/privacy-and-safety.md)
- [docs/build-and-run.md](docs/build-and-run.md)

## Примечание

Исходный длинный текстовый промпт был приведён к GitHub-friendly форме и разложен по документации репозитория. Технические решения, схемы, ограничения и этапы реализации фиксируются в `docs/`.
