# 차곡차곡 (Chagok Chagok)

A Pleos Connect in-car app that stamps every si/gun/gu (city/county/district) where the car stopped onto a map, viewable only while parked.

## How to run

Requirements: Android Studio (bundled JBR), Android SDK, Pleos Connect Emulator v2.1.0 (API 34, x86_64).

```bash
# Build
./gradlew assembleDebug          # Windows cmd/PowerShell: gradlew.bat assembleDebug

# Install & launch on a running emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.chagokchagok.app/.MainActivity
```

## Test environment

- Pleos Connect Emulator v2.1.0 (`system-images;android-34;pleos_car_emulator;x86_64`), RAM 4 GB, cold boot
- minSdk 28, targetSdk 34, Kotlin + Jetpack Compose, Gradle 9.1
- Pleos Connect SDK: NaviHelper 3.0.5, Vehicle 2.3.2
- On the emulator, location is read through Android `LocationManager`; the built-in navigation (NaviHelper) does not follow virtual GPS. Move the virtual GPS with:

```bash
adb emu geo fix 126.9707 37.5547   # longitude latitude
```

## APK location

```
apk/<TeamName>_ChagokChagok_release.apk
```
