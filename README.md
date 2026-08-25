# 추억정리 (Photo Organizer)

매년 같은 날짜에 찍은 사진을 자동으로 모아서 보여주고, 그 자리에서 골라 정리(삭제)할 수 있는
안드로이드 네이티브 앱입니다.

## 주요 기능

- **홈 화면(날짜별 탭)**: 갤러리 전체를 스캔해서 "몇 개 연도에 걸쳐 같은 월/일에 찍힌 사진들"만
  추려 다가오는 날짜순으로 보여줍니다. (예: 8월 25일 · 4개 연도 · 총 23장)
- **홈 화면(장소별 탭)**: 사진의 EXIF GPS 좌표를 읽어 가까운 곳(반경 500m 이내)끼리 묶고,
  지역명(읍/면/동 단위, 역지오코딩)으로 라벨을 붙여 장소별로 모아 보여줍니다.
- **날짜/장소 상세 화면**: 날짜별 탭은 연도별로, 장소별 탭은 촬영일자별로 사진이 묶여서 나오고,
  길게 눌러 여러 장을 선택한 뒤 한 번에 삭제할 수 있습니다.
- **사진 뷰어**: 사진을 탭하면 같은 그룹의 사진들을 옆으로 넘기며 볼 수 있고, 보면서 바로
  삭제할 수 있습니다.
- **오른쪽 위 달력 아이콘**: 아직 1개 연도밖에 없는 날짜도 직접 골라 확인할 수 있습니다.
- **오른쪽 위 연도 아이콘**: 연도를 하나 골라 그 해에 찍힌 모든 사진을 날짜별로 쭉 훑어보며
  정리할 수 있습니다 (홈 화면의 반복 날짜 추리기와 달리, 그 연도의 촬영일 전부를 보여줍니다).
- **선택한 사진 삭제/공유/이동**: 상세 화면에서 사진을 길게 눌러 여러 장을 고른 뒤, 한 번에
  삭제하거나, 다른 앱으로 공유하거나, 갤러리의 다른 폴더(앨범)로 옮길 수 있습니다. 사진 뷰어에서도
  보고 있는 사진을 바로 공유할 수 있습니다.
- **정리 챌린지 알림**: 설정 화면(오른쪽 위 톱니바퀴)에서 알림을 켜고 끄거나 알림 시간을 직접
  정할 수 있습니다. 지정한 시각에 오늘 날짜에 과거 사진이 있으면 "오늘의 정리 챌린지" 알림을
  보내고, 탭하면 바로 그 날짜의 정리 화면으로 이동합니다. 하루라도 정리(삭제)하면 연속일수
  (스트릭)가 올라가고, 홈 화면 상단에 "🔥 N일 연속 정리 챌린지 중!"으로 표시됩니다.

## 기술 스택

- Kotlin, MediaStore(`MediaStore.Images`)로 기기 갤러리의 촬영일자(EXIF `DATE_TAKEN`)를 읽어
  연도 구분 없이 `MonthDay`(월/일) 기준으로 그룹핑
- 장소별 보기는 `ACCESS_MEDIA_LOCATION` 권한 + `MediaStore.setRequireOriginal` +
  androidx `ExifInterface`로 원본 GPS 좌표를 읽고, 자체 구현한 그리디 클러스터링
  (`LocationClusterer`, 순수 Kotlin이라 유닛 테스트 있음)으로 가까운 사진끼리 묶은 뒤
  `Geocoder`로 지역명을 붙입니다.
- 삭제는 Android 11+(API 30) scoped storage용 `MediaStore.createDeleteRequest`로 처리 (시스템
  확인 다이얼로그가 뜬 뒤 실제 삭제) — 그래서 `minSdk = 30`으로 설정했습니다.
- 알림은 `WorkManager`의 1일 주기 `PeriodicWorkRequest`로 예약(재부팅에도 안전)하며, 알림
  on/off와 시각은 `ChallengeSettings`(SharedPreferences)에 저장하고 설정 화면에서 바꿀 수 있습니다.
- Coil(이미지 로딩), ViewBinding, Coroutines
- `PhotoViewerActivity`로 넘기는 사진 목록은 static 홀더(빠른 경로) + 인텐트에 담은 사진 ID
  배열(프로세스가 재생성돼도 Repository에서 다시 불러오는 폴백)을 함께 사용합니다.
- 날짜별/장소별/연도별 세 상세 화면은 "사진을 날짜 섹션으로 묶어 그리드로 보여주고, 선택 후
  삭제·공유·이동한다"는 골격이 완전히 같아서 `BasePhotoDetailActivity`로 공통화했습니다. 하위
  클래스는 무엇을 불러올지(`loadPhotosByDate`)와 섹션 라벨(`sectionLabel`)만 정합니다.
- 폴더 이동은 Android 11+ scoped storage에서 다른 앱이 만든 미디어를 옮겨야 하므로
  `MediaStore.createWriteRequest`로 시스템 동의를 받은 뒤 `RELATIVE_PATH`를 바꿔
  `Pictures/<폴더명>/`으로 옮기는 방식입니다(`PhotoMover`).

## 빌드 방법

⚠️ 이 프로젝트는 **이 세션(클라우드 샌드박스) 안에서는 빌드/실행이 불가능**합니다. 안드로이드
SDK와 Google의 Maven 저장소(`dl.google.com`)에 대한 네트워크 접근이 이 환경에서 막혀 있어서,
AndroidX/AGP 의존성을 받아올 수 없기 때문입니다. (`location-share.apk`는 이 저장소가 아닌 별도
환경에서 빌드된 것으로 보입니다.)

로컬에서 빌드하려면:

1. [Android Studio](https://developer.android.com/studio) 설치 (SDK, Gradle 자동 세팅됨)
2. `photo-organizer/` 폴더를 "Open" 으로 열기
3. 첫 동기화(Gradle Sync)가 끝나면 기기/에뮬레이터에서 실행(▶) — 또는
   `Build > Generate Signed App Bundle / APK`로 APK 생성
4. 커맨드라인으로는 `./gradlew assembleDebug` (Android Studio가 최초 1회 SDK를 세팅해준 뒤 사용 가능)

## 알아두면 좋은 점

- `minSdk 30`(Android 11) 이상 기기를 대상으로 합니다. 그 이전 버전은 삭제 권한 처리 방식이
  복잡해져서(레거시 storage 예외 처리) 이번 범위에서는 제외했습니다.
- 사진 삭제는 실제로 기기에서 영구 삭제됩니다(휴지통 아님). 삭제 직전 안드로이드 시스템이
  자체 확인 다이얼로그를 띄웁니다.
