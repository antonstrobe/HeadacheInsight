# Build And Run

## Bootstrap

Windows:

```powershell
./scripts/bootstrap-windows.ps1
```

Windows interactive build/install helper:

```bat
build-headacheinsight.bat
```

Windows GitHub publish helper:

```bat
publish-headacheinsight-github.bat
```

The root BAT script provides a Russian-language menu for:

- ADB phone check and USB debugging confirmation
- `demoDebug` build and install
- `prodDebug` build and install
- production `APK` / `AAB` build
- Android launcher icon generation from `img/app-icon-master.png`
- GitHub publish flow for `AntonStrobe/HeadacheInsight`
- automatic ASCII alias path handling for Gradle on Windows

The Windows bootstrap script automatically maps the repo to an ASCII `X:` drive when the original path contains non-ASCII characters. This avoids AGP and JVM test-runner issues on Windows paths like `C:\...\Здоровье`.

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

## Android CLI

```bash
cd android-app
./gradlew :app:assembleDemoDebug
./gradlew test
./gradlew connectedDemoDebugAndroidTest
```

On Windows, prefer the bootstrap script or run Gradle from the ASCII `X:\android-app` alias with `--no-daemon` if the repo lives in a non-ASCII path.

## App Icon Source

1. Put the master icon at `img/app-icon-master.png`.
2. Use a square `1024x1024` transparent PNG with alpha channel.
3. Generate Android assets:

```powershell
./scripts/generate-android-icons.ps1
```

4. The GPT-ready prompt for the source artwork lives in:

```text
img/icon-source-gpt-prompt.md
```

## GitHub Publish

```powershell
gh auth login
./scripts/publish-github.ps1
```

The publish helper creates or reuses `AntonStrobe/HeadacheInsight`, configures `origin`, renames the local branch to `main`, commits pending changes, and pushes to GitHub.

## Backend CLI

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -e .[dev]
cp ../.env.example .env
pytest
uvicorn app.main:app --reload
```

## Docker

```bash
cd backend
docker build -t headacheinsight-backend .
docker run --env-file ../.env.example -p 8000:8000 headacheinsight-backend
```
