# 사진정리 (Photo Organizer)

옛날 사진을 골라 정리(삭제/공유/이동)할 수 있게 도와주는 안드로이드 네이티브 앱입니다.

## 주요 기능

- **홈 화면**: 앱을 열면 바로 사진 목록이 아니라 "사진정리 챌린지"와 "날짜별 정리" 두 메뉴만
  심플하게 보여줍니다. 오른쪽 위 톱니바퀴로 챌린지 알림 설정에 들어갈 수 있습니다.
- **사진정리 챌린지**: 정리하고 싶은 연도와 월을 한 번 고르면 그 달 1일부터 하루씩 순서대로
  "오늘의 챌린지"가 됩니다 (예: "2020년 1월 2일 사진 정리"). 그 날 찍은 사진을 보고 정리한 뒤
  "미션 완료"를 누르면 다음 날짜로 넘어갑니다. 진행 상태는 달력 날짜가 아니라 완료 버튼을 누른
  기록으로 저장되므로, 며칠을 건너뛰어도 항상 멈춰있던 그 날짜부터 다시 보여줍니다.
- **날짜별 정리(날짜별 탭)**: 갤러리 전체를 스캔해서 "몇 개 연도에 걸쳐 같은 며칠(예: 15일)에
  찍힌 사진들"만 추려 다가오는 날짜순으로 보여줍니다. 월은 구분하지 않으므로 1월 15일,
  3월 15일, 12월 15일에 찍은 사진이 모두 "15일" 한 그룹으로 모입니다. 상세 화면을 열면
  촬영일(연도+월+일)별로 묶여서 나오고, 여러 연도에 걸쳐 있으면 위쪽에 연도 필터 칩이
  나타나 한 연도만 골라 볼 수 있습니다.
- **날짜별 정리(나라별 탭)**: 사진의 EXIF GPS 좌표로 해외에서 찍은 사진만 나라별로 모아
  보여줍니다(국내 사진은 제외). 나라를 열면 촬영일자별로 사진이 묶여 나옵니다.
- **오른쪽 위 달력/연도 아이콘**: 특정 날짜를 직접 골라보거나, 연도 하나를 골라 그 해에
  찍힌 모든 사진을 날짜별로 쭉 훑어보며 정리할 수 있습니다.
- **선택한 사진 삭제/공유/이동**: 어느 상세 화면에서든 사진마다 항상 떠 있는 체크박스를
  탭해서 고른 뒤, 화면 아래 고정된 버튼으로 한 번에 삭제하거나 다른 앱으로 공유하거나
  갤러리의 다른 폴더(앨범)로 옮길 수 있습니다. 사진 뷰어에서도 보고 있는 사진을 바로
  삭제/공유할 수 있습니다.
- 하루라도 정리(삭제)하면 연속일수(스트릭)가 올라가고, 정리 화면 상단에
  "🔥 N일 연속 정리 챌린지 중!"으로 표시됩니다.

## 기술 스택

- Kotlin, MediaStore(`MediaStore.Images`)로 기기 갤러리의 촬영일자(EXIF `DATE_TAKEN`)를 읽어
  연도·월 구분 없이 며칠(`dayOfMonth`) 기준으로 그룹핑
- 나라별 보기는 `ACCESS_MEDIA_LOCATION` 권한 + `MediaStore.setRequireOriginal` +
  androidx `ExifInterface`로 원본 GPS 좌표를 읽고, 좌표를 약 1도(~111km) 격자로 뭉쳐 격자당
  지오코딩을 한 번만 호출한 뒤 나라 단위로 합칩니다(사진이 많을 때 지오코딩 호출이 너무
  많아지는 걸 막기 위함). 기기 로케일의 국가(집)는 제외합니다.
- 삭제는 Android 11+(API 30) scoped storage용 `MediaStore.createDeleteRequest`로 처리 (시스템
  확인 다이얼로그가 뜬 뒤 실제 삭제) — 그래서 `minSdk = 30`으로 설정했습니다.
- 챌린지 알림은 `WorkManager`의 1일 주기 `PeriodicWorkRequest`로 예약(재부팅에도 안전)하며,
  on/off·시각·진행 상태(연도/월/현재 날짜 커서)는 `ChallengeSettings`(SharedPreferences)에
  저장합니다. 진행 커서는 "미션 완료"를 눌러야만 앞으로 나아갑니다.
- Coil(이미지 로딩), ViewBinding, Coroutines
- `PhotoViewerActivity`로 넘기는 사진 목록은 static 홀더(빠른 경로) + 인텐트에 담은 사진 ID
  배열(프로세스가 재생성돼도 Repository에서 다시 불러오는 폴백)을 함께 사용합니다.
- 날짜별/나라별/연도별 세 상세 화면은 "사진을 날짜 섹션으로 묶어 그리드로 보여주고, 선택 후
  삭제·공유·이동한다"는 골격이 완전히 같아서 `BasePhotoDetailActivity`로 공통화했습니다. 하위
  클래스는 무엇을 불러올지(`loadPhotosByDate`)와 섹션 라벨(`sectionLabel`)만 정합니다. 챌린지
  화면은 상태(권한 대기/연도·월 선택/진행 중/완료)가 아예 달라 별도 액티비티로 뒀습니다.
- 폴더 이동은 Android 11+ scoped storage에서 다른 앱이 만든 미디어를 옮겨야 하므로
  `MediaStore.createWriteRequest`로 시스템 동의를 받은 뒤 `RELATIVE_PATH`를 바꿔
  `Pictures/<폴더명>/`으로 옮기는 방식입니다(`PhotoMover`).
- 삭제/공유/이동 버튼은 툴바 메뉴 아이콘이 아니라 화면에 항상 고정된 일반 뷰 버튼입니다 —
  액션바 메뉴 표시 방식은 기기/테마에 따라 달라질 여지가 있어, 항상 같은 자리에 보이는 쪽을
  택했습니다.

## 화면 구조

```
HomeActivity (런처)
├─ ChallengeActivity   — 사진정리 챌린지 (연도/월 선택 → 하루씩 진행)
│   └─ PhotoViewerActivity
└─ MainActivity         — 날짜별 정리 (날짜별/나라별 탭)
    ├─ DetailActivity        — 특정 며칠(월 무관)의 촬영일별 사진
    ├─ LocationDetailActivity — 특정 나라의 날짜별 사진
    ├─ YearDetailActivity     — 특정 연도의 날짜별 사진
    └─ PhotoViewerActivity
SettingsActivity — 챌린지 알림 on/off·시간 (Home 우측 위 톱니바퀴에서 진입)
```

## 빌드 방법

⚠️ 이 프로젝트는 **이 세션(클라우드 샌드박스) 안에서는 빌드/실행이 불가능**합니다. 안드로이드
SDK와 Google의 Maven 저장소(`dl.google.com`)에 대한 네트워크 접근이 이 환경에서 막혀 있어서,
AndroidX/AGP 의존성을 받아올 수 없기 때문입니다.

로컬에서 빌드하려면:

1. [Android Studio](https://developer.android.com/studio) 설치 (SDK, Gradle 자동 세팅됨)
2. `photo-organizer/` 폴더를 "Open" 으로 열기
3. 첫 동기화(Gradle Sync)가 끝나면 기기/에뮬레이터에서 실행(▶) — 또는
   `Build > Generate Signed App Bundle / APK`로 APK 생성
4. 커맨드라인으로는 `./gradlew assembleDebug` (Android Studio가 최초 1회 SDK를 세팅해준 뒤 사용 가능)

CI(GitHub Actions, `.github/workflows/build.yml`)가 push마다 유닛 테스트 + 디버그 APK 빌드를
자동으로 돌리고 아티팩트로 올려줍니다. 서명은 저장소에 커밋된 고정 디버그 키(`app/debug.keystore`)를
써서, 어디서 빌드하든 항상 같은 서명이 나와 기기에 이미 설치된 앱 위에 덮어 설치할 수 있습니다.

## 알아두면 좋은 점

- `minSdk 30`(Android 11) 이상 기기를 대상으로 합니다. 그 이전 버전은 삭제 권한 처리 방식이
  복잡해져서(레거시 storage 예외 처리) 이번 범위에서는 제외했습니다.
- 사진 삭제는 실제로 기기에서 영구 삭제됩니다(휴지통 아님). 삭제 직전 안드로이드 시스템이
  자체 확인 다이얼로그를 띄웁니다.
