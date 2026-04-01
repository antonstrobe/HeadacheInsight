# HeadacheInsight

<p align="center">
  <a href="https://github.com/AntonStrobe/HeadacheInsight/releases/download/v0.1.1-mvp-preview/app-prod-debug.apk">
    <img src="https://img.shields.io/badge/Download-APK%20Preview-2ea043?style=for-the-badge&logo=android&logoColor=white" alt="Download APK Preview" />
  </a>
  <a href="./quick-start-windows-admin.bat">
    <img src="https://img.shields.io/badge/Windows-One--click%20Setup-1f6feb?style=for-the-badge&logo=windows&logoColor=white" alt="Windows One-click Setup" />
  </a>
  <a href="./LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-f59e0b?style=for-the-badge" alt="MIT License" />
  </a>
</p>

<p align="center">
  <a href="#ru">
    <img src="https://img.shields.io/badge/README-RU-1f6feb?style=flat-square" alt="README Russian" />
  </a>
  <a href="#en">
    <img src="https://img.shields.io/badge/README-EN-1f6feb?style=flat-square" alt="README English" />
  </a>
</p>

[![Open GitHub repository](docs/assets/github-project-card.png)](https://github.com/AntonStrobe/HeadacheInsight)

> Open-source MIT-licensed foundation for a privacy-first headache and migraine journal with offline-first capture, voice input, and optional cloud analysis.

---

<a id="ru"></a>
## Русский

### Что это

HeadacheInsight — Android-приложение с открытым исходным кодом и серверная основа для фиксации и анализа головной боли и мигрени. Проект строится как система с приоритетом приватности, офлайна и голосового ввода: пользователь быстро фиксирует эпизод, хранит данные локально и при желании подключает структурированный облачный анализ через свой сервер.

### Что уже реализовано

- нативный Android-клиент на Kotlin и Jetpack Compose
- строгая многомодульная архитектура с разделением `ui / domain / data`
- локальное хранилище на Room как основной источник структурированных данных
- onboarding, домашний экран, быстрый лог, профиль, история, настройки и основа отчётов
- локальный движок проверки опасных признаков
- стартовый банк вопросов, локальный движок подбора вопросов и облачные контракты
- FastAPI-сервер для транскрибации и анализа
- APK-сборка на GitHub Releases

### MIT и свобода использования

Проект распространяется по лицензии [MIT](LICENSE).

Это означает, что вы можете:

- свободно использовать код в личных и коммерческих проектах
- изменять, форкать и распространять его
- встраивать его в собственные продукты
- монетизировать свои сборки и производные решения так, как вам нужно

Важно:

- сохраняйте текст лицензии MIT и уведомление об авторстве
- самостоятельно отвечайте за медицинские, юридические и регуляторные требования своей юрисдикции
- приложение не является медицинским изделием и не заменяет врача

### OpenAI / ChatGPT API

Текущий сценарий подключения облачного анализа такой:

1. Вы запускаете свой сервер.
2. В приложении открываете `Настройки`.
3. Вводите:
   - URL сервера
   - свой OpenAI / ChatGPT API-ключ
   - модели для анализа, вопросов и транскрибации

Как это работает:

- ключ вводится пользователем прямо в Android-приложении
- ключ шифруется и хранится локально в изолированном хранилище Android на базе Keystore
- Android-клиент не обращается к OpenAI напрямую
- клиент отправляет запросы только на ваш сервер по HTTPS
- сервер может использовать ключ, переданный приложением в заголовках, и не обязан хранить его у себя

### Скачать APK

- APK preview:
  `https://github.com/AntonStrobe/HeadacheInsight/releases/download/v0.1.1-mvp-preview/app-prod-debug.apk`
- Страница релиза:
  `https://github.com/AntonStrobe/HeadacheInsight/releases/tag/v0.1.1-mvp-preview`

### Быстрый старт

#### Windows

1. Запустите [quick-start-windows-admin.bat](quick-start-windows-admin.bat) от имени администратора.
2. Скрипт сам:
   - установит Chocolatey при необходимости
   - поставит JDK, Python, Git и Android command-line tools
   - примет Android licenses
   - соберёт первую debug-сборку
   - откроет меню сборки и установки

#### Linux

```bash
chmod +x ./quick-start-linux.sh
./quick-start-linux.sh
```

#### macOS

```bash
chmod +x ./quick-start-macos.sh
./quick-start-macos.sh
```

#### Сервер

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -e .[dev]
uvicorn app.main:app --reload
```

После запуска сервера откройте приложение и заполните раздел `Настройки`.

### Структура репозитория

```text
/android-app    Android-клиент
/backend        FastAPI-сервер для анализа и транскрибации
/docs           Архитектура, приватность, схемы, инструкции по сборке
/img            Исходные изображения и материалы для иконок
/scripts        Скрипты bootstrap, сборки, публикации и генерации ресурсов
```

### Основные документы

- [docs/build-and-run.md](docs/build-and-run.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/architecture-decisions.md](docs/architecture-decisions.md)
- [docs/privacy-and-safety.md](docs/privacy-and-safety.md)
- [docs/analysis-schema.md](docs/analysis-schema.md)
- [docs/question-schema.md](docs/question-schema.md)

---

<a id="en"></a>
## English

### What This Is

HeadacheInsight is an open-source Android app and backend foundation for headache and migraine tracking and analysis. It is built as a privacy-first, offline-first, voice-first system: users can capture episodes quickly, keep data locally, and optionally enable structured cloud analysis through their own server.

### What Is Already Included

- native Android client built with Kotlin and Jetpack Compose
- strict multi-module architecture with `ui / domain / data` separation
- Room-based local storage as the primary source of structured data
- onboarding, home, quick log, profile, history, settings, and report foundation
- local deterministic red-flag safety engine
- seed questions, local question engine, and cloud contracts
- FastAPI backend for transcription and analysis
- APK release on GitHub Releases

### MIT License and Freedom of Use

This project is released under the [MIT License](LICENSE).

You are free to:

- use the code in private or commercial products
- modify, fork, and redistribute it
- build your own product or business on top of it
- ship your own versions and monetize them however you want

Important:

- keep the MIT license text and copyright notice
- handle medical, legal, and regulatory compliance for your own use case and jurisdiction
- the app is not a medical device and does not replace a clinician

### OpenAI / ChatGPT API

The current cloud-analysis flow is:

1. Run your backend.
2. Open `Settings` inside the Android app.
3. Enter:
   - backend URL
   - your OpenAI / ChatGPT API key
   - model names for analysis, follow-up questions, and transcription

Design details:

- the API key is entered by the user inside the Android app
- the key is encrypted and stored locally in Android isolated storage backed by Keystore
- the Android client does not call OpenAI directly
- the app sends requests only to your server over HTTPS
- the backend can use the key forwarded by the app and does not need to persist it

### Download APK

- APK preview:
  `https://github.com/AntonStrobe/HeadacheInsight/releases/download/v0.1.1-mvp-preview/app-prod-debug.apk`
- Release page:
  `https://github.com/AntonStrobe/HeadacheInsight/releases/tag/v0.1.1-mvp-preview`

### Quick Start

#### Windows

1. Run [quick-start-windows-admin.bat](quick-start-windows-admin.bat) as Administrator.
2. The script will:
   - install Chocolatey if needed
   - install JDK, Python, Git, and Android command-line tools
   - accept Android licenses
   - build the first debug APK
   - open the build/install helper menu

#### Linux

```bash
chmod +x ./quick-start-linux.sh
./quick-start-linux.sh
```

#### macOS

```bash
chmod +x ./quick-start-macos.sh
./quick-start-macos.sh
```

#### Backend

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -e .[dev]
uvicorn app.main:app --reload
```

After the backend is running, open the app and fill out the `Settings` screen.

### Repository Layout

```text
/android-app    Android client
/backend        FastAPI backend for analysis and transcription
/docs           Architecture, privacy, schemas, and build docs
/img            Source images and icon materials
/scripts        Bootstrap, build, publish, and asset helpers
```

### Core Docs

- [docs/build-and-run.md](docs/build-and-run.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/architecture-decisions.md](docs/architecture-decisions.md)
- [docs/privacy-and-safety.md](docs/privacy-and-safety.md)
- [docs/analysis-schema.md](docs/analysis-schema.md)
- [docs/question-schema.md](docs/question-schema.md)
