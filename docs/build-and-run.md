# Build And Run

## Quick Start

### Windows

Run [quick-start-windows-admin.bat](../quick-start-windows-admin.bat) as Administrator.

This single entry point will:

- install Chocolatey if it is missing
- install JDK 21, Python, Git, and Android command-line tools
- accept Android SDK licenses
- build the first debug APK
- open the Russian-language build/install helper

### Linux

```bash
chmod +x ./quick-start-linux.sh
./quick-start-linux.sh
```

### macOS

```bash
chmod +x ./quick-start-macos.sh
./quick-start-macos.sh
```

## Windows Helper Menu

```bat
build-headacheinsight.bat
```

The helper menu provides:

- phone detection through ADB
- `demoDebug` build and install
- `prodDebug` build and install
- production `APK` / `AAB` build
- Android launcher icon generation from `img/app-icon-master.png`
- GitHub publish flow for `AntonStrobe/HeadacheInsight`

The Windows scripts automatically map the repo to an ASCII `X:` drive if the original path contains non-ASCII characters. This avoids AGP and JVM test-runner issues on Windows paths with Cyrillic characters.

## Backend

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -e .[dev]
pytest
uvicorn app.main:app --reload
```

## Cloud Analysis Configuration

The recommended setup is:

1. Start the backend.
2. Open `Settings` in the Android app.
3. Fill in:
   - backend URL
   - OpenAI / ChatGPT API key
   - analysis model
   - follow-up question model
   - transcription model

Security notes:

- the API key is stored locally inside the Android app in Keystore-backed isolated storage
- the Android client does not call OpenAI directly
- the app sends requests only to your backend over HTTPS
- the backend can use the key forwarded by the app and does not need to store it persistently

If you want a server-side fallback for self-hosting, you can still provide the same values through `backend/.env`.

## Android CLI

```bash
cd android-app
./gradlew :app:assembleDemoDebug
./gradlew test
./gradlew connectedDemoDebugAndroidTest
```

On Windows, prefer the bootstrap script or run Gradle from the ASCII `X:\\android-app` alias with `--no-daemon` if the repo lives in a non-ASCII path.

## App Icon Source

1. Put the master icon at `img/app-icon-master.png`.
2. Use a square `1024x1024` transparent PNG with alpha channel.
3. Generate Android assets:

```powershell
./scripts/generate-android-icons.ps1
```

The GPT-ready prompt for the source artwork lives in:

```text
img/icon-source-gpt-prompt.md
```

## GitHub Publish

```powershell
gh auth login
./scripts/publish-github.ps1
```

The publish helper creates or reuses `AntonStrobe/HeadacheInsight`, configures `origin`, renames the local branch to `main`, commits pending changes, and pushes to GitHub.

## Docker

```bash
cd backend
docker build -t headacheinsight-backend .
docker run --env-file ./.env.example -p 8000:8000 headacheinsight-backend
```
