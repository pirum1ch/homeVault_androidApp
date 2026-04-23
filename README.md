# HomeVault Android Client

Android client for the HomeVault personal NAS/file storage system.

## Requirements

- Android 8.0+ (API 26+)
- Backend running at `http://localhost:8080` (see [HomeVault backend](../HomeVault))
- Android Studio Hedgehog or newer (for development)

## Features

- **Login** with username/password; optional biometric (fingerprint/face) on subsequent opens
- **File list** — paginated, pull-to-refresh, infinite scroll
- **File detail** — metadata, preview icon, download to Downloads folder, delete with confirmation
- **Upload** — pick files via bottom action bar or share files from any app (share sheet integration)
- **Upload queue** — background uploads via WorkManager, retry on failure, cancel pending jobs
- **Auto-refresh** — configurable file list polling (off / 1s / 5s / 10s), persisted across sessions, displayed as a pill in the top bar
- **Profile** — view/edit server URLs, toggle biometric, set auto-refresh interval, logout
- **Network switching** — automatically switches between local (LAN) and public URL with 2 s health check and 5 min cooldown
- **Session handling** — JWT interceptor auto-attaches Bearer token; 401 redirects to login

## Build

```bash
# Debug APK
./gradlew assembleDebug          # Linux/macOS
gradlew.bat assembleDebug        # Windows

# Release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Lint
./gradlew lint
```

Requires JDK 21. Use Android Studio's bundled JDK (Gradle 8.9 is incompatible with JDK 22+):

```powershell
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

```bash
# Linux/macOS
export JAVA_HOME="$ANDROID_STUDIO_HOME/jbr"
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## Install on device / emulator

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Configuration

On first login, default credentials are `admin` / `admin` (set by the backend's `AdminUserInitializer`).

Server URLs are configured in **Profile → server**:
| Setting | Default | Notes |
|---|---|---|
| Public URL | `http://10.0.2.2:8080` | `10.0.2.2` = host machine from emulator |
| Local URL | _(empty)_ | LAN address, e.g. `http://192.168.1.10:8080` |

Auto-refresh interval is set in **Profile → auto-refresh** and persists across restarts:
| Option | Interval |
|---|---|
| нет | disabled |
| 1 сек | 1 second |
| 5 сек | 5 seconds |
| 10 сек | 10 seconds |

## Architecture

```
app/
├── data/
│   ├── database/       Room — upload queue persistence
│   ├── network/        Retrofit + OkHttp, JWT interceptor, DTOs
│   ├── preferences/    EncryptedSharedPreferences wrapper
│   └── repository/     AuthRepository, FileRepository, NetworkSwitcher
├── ui/screens/         Jetpack Compose screens (Login, FileList, FileDetail, Profile, UploadQueue)
├── viewmodel/          AndroidViewModel + StateFlow/SharedFlow
├── worker/             UploadWorker, DownloadWorker (WorkManager CoroutineWorker)
├── share/              ShareReceiverActivity — handles ACTION_SEND intents
└── util/               BiometricHelper, NotificationHelper, FileUtils, Constants
```

**Stack:** Kotlin · Jetpack Compose · Material3 · MVVM · Room · WorkManager · Retrofit · OkHttp · Coil · EncryptedSharedPreferences · BiometricPrompt

## Related projects

- **Backend:** `../HomeVault` — Spring Boot REST API
- **Frontend:** `../frontEnd_HomeVault` — web client
