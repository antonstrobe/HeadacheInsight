# HeadacheInsight

<p align="center">
  <a href="https://github.com/AntonStrobe/HeadacheInsight/releases/download/v0.1.0-mvp-preview/app-prod-debug.apk">
    <img src="https://img.shields.io/badge/Download-APK%20Preview-2ea043?style=for-the-badge&logo=android&logoColor=white" alt="Download APK Preview" />
  </a>
  <a href="https://github.com/AntonStrobe/HeadacheInsight/releases/tag/v0.1.0-mvp-preview">
    <img src="https://img.shields.io/badge/Open-GitHub%20Release-1f6feb?style=for-the-badge&logo=github&logoColor=white" alt="Open GitHub Release" />
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

> Open-source MIT-licensed foundation for a privacy-first headache and migraine journal.
> You can use it privately, commercially, modify it, redistribute it, and build your own product on top of it under the MIT license terms.

---

<a id="ru"></a>
## Русский

### Что это

HeadacheInsight — open-source Android-приложение и backend foundation для фиксации и анализа головной боли и мигрени. Проект строится как `privacy-first`, `offline-first`, `voice-first` система: пользователь может быстро зафиксировать эпизод, сохранить данные локально и при необходимости подключить структурированный облачный анализ через backend proxy.

### Что уже есть

- нативный Android-клиент на Kotlin + Jetpack Compose
- multi-module архитектура с разделением `ui / domain / data`
- локальное хранилище на Room как primary source of truth
- onboarding, home, quick log, profile, history, settings, reports foundation
- локальный red-flag safety engine
- seed questions, локальный question engine и cloud contracts
- FastAPI backend proxy для транскрибации и анализа
- release APK на GitHub Releases

### APK и запуск

- Скачать APK:
  `https://github.com/AntonStrobe/HeadacheInsight/releases/download/v0.1.0-mvp-preview/app-prod-debug.apk`
- Открыть страницу релиза:
  `https://github.com/AntonStrobe/HeadacheInsight/releases/tag/v0.1.0-mvp-preview`
- Точные команды сборки и запуска:
  [docs/build-and-run.md](docs/build-and-run.md)

### MIT лицензия и свобода использования

Проект распространяется по лицензии [MIT](LICENSE).

Это означает, что вы можете:

- свободно использовать код в личных и коммерческих проектах
- изменять, форкать и переиздавать его
- встраивать его в свои продукты и монетизировать как угодно
- распространять собственные сборки и производные решения

Важно:

- сохраняйте текст лицензии MIT и уведомление об авторстве
- самостоятельно отвечайте за соответствие медицинским, юридическим и регуляторным требованиям вашей юрисдикции
- приложение не является медицинским изделием и не заменяет врача

### OpenAI / ChatGPT API

Если вы хотите включить облачный анализ и ответы через OpenAI API:

1. Скопируйте `backend/.env.example` в `backend/.env`.
2. Укажите ключ:

```env
OPENAI_API_KEY=sk-your-real-key
OPENAI_ANALYSIS_MODEL=gpt-4.1
OPENAI_QUESTION_MODEL=gpt-4.1-mini
OPENAI_TRANSCRIBE_MODEL=gpt-4o-transcribe
```

3. Запустите backend:

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -e .[dev]
uvicorn app.main:app --reload
```

4. В Android-приложении включите `cloud analysis` и укажите URL backend.

Критично:

- ключ OpenAI не хранится в Android-приложении
- Android-клиент не вызывает OpenAI напрямую
- ключ задаётся только на backend/proxy
- без ключа и без интернета приложение всё равно остаётся полезным в офлайн-режиме

### Быстрый старт

Windows:

```powershell
./scripts/bootstrap-windows.ps1
build-headacheinsight.bat
```

Linux:

```bash
chmod +x ./scripts/bootstrap-linux.sh
./scripts/bootstrap-linux.sh
```

macOS:

```bash
chmod +x ./scripts/bootstrap-macos.sh
./scripts/bootstrap-macos.sh
```

### Структура репозитория

```text
/android-app    Android client
/backend        FastAPI proxy and schema-driven OpenAI integration
/docs           Architecture, privacy, safety, schemas, build docs
/img            Source icon assets and generation prompts
/scripts        Bootstrap, build, publish, asset helpers
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

HeadacheInsight is an open-source Android app and backend foundation for headache and migraine tracking and analysis. The project is built as a `privacy-first`, `offline-first`, `voice-first` system: users can capture episodes quickly, keep data locally, and optionally enable structured cloud analysis through a backend proxy.

### What Is Already Included

- native Android client built with Kotlin + Jetpack Compose
- strict multi-module architecture with `ui / domain / data` separation
- Room-based local storage as the primary source of truth
- onboarding, home, quick log, profile, history, settings, and reports foundation
- local deterministic red-flag safety engine
- seed questions, local question engine, and cloud contracts
- FastAPI backend proxy for transcription and analysis
- downloadable APK release on GitHub Releases

### APK and Launch

- Download APK:
  `https://github.com/AntonStrobe/HeadacheInsight/releases/download/v0.1.0-mvp-preview/app-prod-debug.apk`
- Open release page:
  `https://github.com/AntonStrobe/HeadacheInsight/releases/tag/v0.1.0-mvp-preview`
- Exact build and run commands:
  [docs/build-and-run.md](docs/build-and-run.md)

### MIT License and Freedom of Use

This project is released under the [MIT License](LICENSE).

That means you are free to:

- use the code in private or commercial products
- modify, fork, and redistribute it
- build your own business or product on top of it
- ship your own versions and monetize them as you wish

Important:

- keep the MIT license text and copyright notice
- handle medical, legal, and regulatory compliance for your own use case and jurisdiction
- the app is not a medical device and does not replace a clinician

### OpenAI / ChatGPT API

If you want to enable cloud analysis and OpenAI-powered responses:

1. Copy `backend/.env.example` to `backend/.env`.
2. Set your key:

```env
OPENAI_API_KEY=sk-your-real-key
OPENAI_ANALYSIS_MODEL=gpt-4.1
OPENAI_QUESTION_MODEL=gpt-4.1-mini
OPENAI_TRANSCRIBE_MODEL=gpt-4o-transcribe
```

3. Start the backend:

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -e .[dev]
uvicorn app.main:app --reload
```

4. In the Android app, enable `cloud analysis` and set the backend URL.

Critical design rule:

- the OpenAI API key is never stored in the Android app
- the Android client never calls OpenAI directly
- the key is configured only on the backend/proxy
- the app still remains useful offline without any API key

### Quick Start

Windows:

```powershell
./scripts/bootstrap-windows.ps1
build-headacheinsight.bat
```

Linux:

```bash
chmod +x ./scripts/bootstrap-linux.sh
./scripts/bootstrap-linux.sh
```

macOS:

```bash
chmod +x ./scripts/bootstrap-macos.sh
./scripts/bootstrap-macos.sh
```

### Repository Layout

```text
/android-app    Android client
/backend        FastAPI proxy and schema-driven OpenAI integration
/docs           Architecture, privacy, safety, schemas, build docs
/img            Source icon assets and generation prompts
/scripts        Bootstrap, build, publish, asset helpers
```

### Core Docs

- [docs/build-and-run.md](docs/build-and-run.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/architecture-decisions.md](docs/architecture-decisions.md)
- [docs/privacy-and-safety.md](docs/privacy-and-safety.md)
- [docs/analysis-schema.md](docs/analysis-schema.md)
- [docs/question-schema.md](docs/question-schema.md)
