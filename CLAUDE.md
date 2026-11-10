# CLAUDE.md

Chagok Chagok (차곡차곡): a Pleos Connect (Android Automotive OS) app for the Hyundai Motor Group In-Car App Development Contest.
The car stamps each si/gun/gu where it stopped onto a map; the map is viewed only while parked.

- Talk to the user in Korean. Code, comments, commit messages and file names in English.
- No personal names, emails or school names anywhere in the repo.
- Package: `com.chagokchagok.app` (locked).

## Machine setup (Windows x64)

| Item | Location / version |
|---|---|
| Android Studio | `C:\Program Files\Android\Android Studio` (2026.2.2) |
| JDK (JAVA_HOME) | `C:\Program Files\Android\Android Studio\jbr` (JBR 25) |
| Android SDK (ANDROID_HOME) | `%LOCALAPPDATA%\Android\Sdk` |
| adb | `%ANDROID_HOME%\platform-tools\adb.exe` |
| emulator | `%ANDROID_HOME%\emulator\emulator.exe` (WHPX acceleration) |
| sdkmanager / avdmanager | `%ANDROID_HOME%\cmdline-tools\latest\bin\` |
| System image | `system-images;android-34;pleos_car_emulator;x86_64` (Pleos Connect v2.1.0) |
| AVD | `Pleos_Connect_v2.1.0` (RAM 4096 MB, cold boot) at `%USERPROFILE%\.android\avd\` |

The Pleos image repo is registered for sdkmanager in `%USERPROFILE%\.android\repositories.cfg`
(`https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`).
CRN is not required from Pleos Connect Emulator 2.1.0.

## Commands that work (Git Bash, from C:\dev\hyundai_app)

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export MSYS_NO_PATHCONV=1   # REQUIRED: otherwise Git Bash rewrites /sdcard/... into a Windows path
A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"

# Boot emulator (run in background; ~30 s to boot)
"$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe" -avd Pleos_Connect_v2.1.0 -no-snapshot-save
"$A" wait-for-device; "$A" shell getprop sys.boot_completed   # 1 = ready

# Build (check the output for "BUILD SUCCESSFUL" before installing)
./gradlew assembleDebug --console=plain

# Install & launch
"$A" install -r app/build/outputs/apk/debug/app-debug.apk
"$A" shell am start -n com.chagokchagok.app/.MainActivity

# Screenshot (debug-screens/ is git-ignored)
"$A" shell screencap -p /sdcard/s.png && "$A" pull /sdcard/s.png debug-screens/

# Move virtual GPS (longitude latitude)
"$A" emu geo fix 126.9707 37.5547   # Seoul
"$A" emu geo fix 129.2247 35.8562   # Gyeongju
```

PowerShell: use `.\gradlew.bat assembleDebug`. Never redirect adb binary output (e.g. `screencap > file`) in PowerShell; use `screencap -p /sdcard/...` then `adb pull`.

## Emulator facts (verified)

- `LocationManager` (GPS provider) follows `adb emu geo fix`.
- NaviHelper connects and answers `getCurrentLocationInfo()`, but always returns the navigation's own fixed position
  (37.41272, 127.09468, address "수정구 시흥동"); it ignores virtual GPS. Confirmed by the contest office.
- `CurrentLocationInfo.address` looks like "<gu> <dong>" (si can be missing), so do not rely on it alone for si/gun/gu.
- `CarPropertyManager` IGNITION_STATE: not available (no property config) -> "unsupported".
  Vehicle SDK has an `IgnitionState` constant but no public getter. Use `Vehicle.getGear()` for parked detection instead.
- The image is a user build: `cmd car_service get-property-value` is blocked.

## Build setup

- Kotlin + Jetpack Compose, Gradle 9.1 / AGP 9.0.1 / Kotlin 2.3.20, version catalog in `gradle/libs.versions.toml`.
- minSdk 28, targetSdk 34 (Pleos image), compileSdk 36 (required by current AndroidX).
- `useLibrary("android.car")` for CarUxRestrictionsManager / CarPropertyManager.
- Pleos Maven repo: `https://nexus-playground.pleos.ai/repository/maven-releases/`
  (`ai.pleos.playground:NaviHelper:3.0.5`, `ai.pleos.playground:Vehicle:2.3.2`).
- Permissions: `pleos.car.permission.NAVI_ROUTE` (NaviHelper get*Info), location, `android.car.permission.CAR_POWERTRAIN`.
  Vehicle SDK adds `VEHICLE_BINDING` via its AAR. Each Vehicle domain needs its own `pleos.car.permission.CAR_*`.
- NaviHelper / Vehicle API reference pages render with JavaScript. Verify signatures with `javap` on the AAR in
  `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\ai.pleos.playground\` instead of guessing.

## Project rules

- Store only the region (si/gun/gu) and the date. Never persist raw coordinates or exact times.
- No UI while driving: the manifest does NOT declare distraction optimization; hide the screen whenever
  CarUxRestrictions require distraction optimization (see `car/CarStateMonitor.kt`).
- Read location through one interface (`location/LocationSource.kt`): NaviHelper on real cars, LocationManager on the emulator.
- No external map SDKs or API keys. Region boundaries are drawn from bundled data.
- Stay under ~400 MB memory. No always-on background work; work only while the activity is started.
- Keep credentials (CRN, keys, tokens, keystores) out of git; `.gitignore` already covers the usual file names.
- `app/src/main/kotlin/com/chagokchagok/app/debug/` is a temporary smoke-test screen, not the real UI.

## Docs (official sources beat memory)

- Contest dev environment: https://appdevchallenge.auto-hmg.io/guide/development
- Pleos Connect docs entry: https://document.pleos.ai/docs/pleos-only/getting-started
- Emulator install guide: https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Emulator & resources download: https://pleos.ai/playground/discover/resources
- NaviHelper SDK: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro
  (importing-sdk, declaring-permissions, initialization-and-release are linked from there)
- Vehicle SDK setup / permissions: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup ,
  https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
- Driving restriction guide: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- API compatibility: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability
- My Project / SDK application: https://document.pleos.ai/docs/pleos-only/create-my-project

Pleos docs move often. If a link is dead, navigate from the docs entry page. Never invent Pleos-specific values.
