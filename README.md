# 차곡차곡 (Chagok Chagok)

**팀명: Nautilus**

> 차를 세운 시·군·구마다 지도에 도장을 차곡차곡 찍어 주는 Pleos Connect 차량용 여행 기록 앱 (주차 중에만 사용)

## 주요 기능

- **위치 동의**: 처음 실행할 때 저장하는 것(시·군·구 이름 · 날짜 · 그날 순서)과 저장하지 않는 것(정확한 좌표 · 이동 경로 · 시각)을 보여 주고 동의를 받습니다. "나중에"를 고르면 위치를 전혀 읽지 않습니다.
- **도장 찍기**: 주차 중 앱을 열면 위치를 한 번 읽어, 함께 들어 있는 경계 데이터(230개 시·군·구)로 지역을 찾아 도장을 찍습니다.
  새 지역은 STAMPED!, 같은 날 같은 지역은 ALREADY STAMPED, 다른 날 다시 오면 WELCOME BACK(방문 횟수 +1).
- **지도**: 지도 SDK 없이 Compose Canvas로 230개 시·군·구를 그립니다. 오늘 찍은 곳과 이전에 찍은 곳을 색으로 구분하고, 오늘 도장은 순서대로 점선으로 잇습니다.
- **오늘 누구랑?**: 가족 / 연인 / 친구 / 혼자를 고르면 오늘 도장에 저장되고, 지도 탭(나의 지도 / 우리 가족 / 연인 / 친구)에서 따로 볼 수 있습니다.
- **내비게이션 유도**: 도장을 확인한 뒤 화면 전체를 쓰고 있을 때 "다음 도장은 내비로 찾아가요" 카드로 차량 내비게이션을 열 수 있습니다.
- **주행 중 차단**: 주행 제한(CarUxRestrictions)이 걸리면 화면 전체가 정적인 안내로 바뀌고 어떤 조작도 받지 않습니다. 주차하면 이전 화면으로 돌아옵니다.
- 모든 기록은 차 안(Room DB)에만 저장되고 서버로 보내지 않습니다. 백그라운드 작업은 없습니다.

## 실행 방법

필요한 것: Android Studio(내장 JBR), Android SDK, Pleos Connect Emulator v2.1.0 (API 34, x86_64)

```bash
# 빌드 (Windows cmd/PowerShell: gradlew.bat assembleDebug)
./gradlew assembleDebug

# 실행 중인 에뮬레이터에 설치하고 실행
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.chagokchagok.app/.MainActivity

# 가상 GPS 이동 (경도 위도 순서)
adb emu geo fix 127.09468 37.41272   # 경기도 성남시
adb emu geo fix 129.2247 35.8562     # 경상북도 경주시
adb emu geo fix 126.5312 33.4996     # 제주특별자치도 제주시
adb emu geo fix 126.4407 37.4602     # 인천광역시 영종구
```

위치를 옮긴 뒤 앱을 다시 열면(재개되면) 그 위치에 도장이 찍힙니다.

### 릴리스 빌드

`./gradlew assembleRelease`는 저장소 밖의 `~/.chagok/keystore.properties`(storeFile, storePassword, keyAlias, keyPassword)가 있으면 그 키로 서명하고, 없으면 서명하지 않은 APK를 만듭니다. 키스토어와 비밀번호는 git에 올리지 않습니다.

## 테스트 환경

- Pleos Connect Emulator v2.1.0 (`system-images;android-34;pleos_car_emulator;x86_64`), RAM 4 GB, cold boot
- minSdk 28, targetSdk 34, compileSdk 36, Kotlin + Jetpack Compose, Gradle 9.1
- Pleos Connect SDK: Vehicle 2.3.2
- 에뮬레이터에서는 Android `LocationManager`로 위치를 읽습니다(내장 내비게이션은 가상 GPS를 따르지 않습니다).
- 주행 상태는 Pleos Virtual Console로 넣었습니다: `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`에서 기어 D, 속도 30 km/h.
- 확인한 항목: 첫 실행 0 STAMPS, 네 지점 순서대로 도장, ALREADY STAMPED, 다음 날 WELCOME BACK, 동행자 저장과 탭 필터, 재시작 후 유지, 주행 중 차단, 릴리스 APK 설치·실행

## APK 위치

```
apk/Nautilus_ChagokChagok_release.apk
```

## 데이터 출처

시·군·구 경계: 통계청 SGIS 기반 `vuski/admdongkor` (CC BY 4.0). 자세한 내용은 `NOTICE.md`, `design/README.md`.
