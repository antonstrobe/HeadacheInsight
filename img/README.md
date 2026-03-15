# App Icon Source

Положите мастер-файл иконки сюда:

- `img/app-icon-master.png`

Также поддерживается уже добавленный файл:

- `img/icon.png`

Если скрипт видит `img/icon.png`, он автоматически копирует его в канонический путь `img/app-icon-master.png` и строит иконки из него.

Требования к исходнику:

- `PNG`
- квадрат `1024x1024`
- прозрачный фон с alpha channel
- один центральный символ без текста
- безопасные отступы по краям, чтобы значок не упирался в mask launcher icon
- без белой подложки и без рамки по краям

После добавления файла:

```powershell
./scripts/generate-android-icons.ps1
```

Или через русскоязычный helper:

```bat
build-headacheinsight.bat
```

Скрипт создаст:

- adaptive launcher icon для Android
- `mipmap-*` PNG для legacy surfaces
- foreground PNG для `mipmap-anydpi-v26`
- preview/export файлы в `img/export`
