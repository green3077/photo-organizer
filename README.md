# 사진정리 (Photo Organizer)

옛날 사진을 골라 정리(삭제/공유/이동)할 수 있게 도와주는 안드로이드 네이티브 앱입니다.

## 주요 기능

- **홈 화면**: "날짜별 정리"/"달력으로 보기"/"장소별 정리" 세 메뉴만 심플하게 보여줍니다.
  오른쪽 위에는 휴지통 아이콘이 있고, 화면 아래에는 배너 광고(AdMob)가 붙습니다.
- **날짜별 정리**: 일별(며칠 단위, 월 무관)과 월별 중 골라서 봅니다. 일별은 "몇 개 연도에
  걸쳐 같은 며칠(예: 15일)에 찍힌 사진들"만 추려 다가오는 날짜순으로 보여줍니다. 월은
  구분하지 않으므로 1월 15일, 3월 15일, 12월 15일에 찍은 사진이 모두 "15일" 한 그룹으로
  모입니다. 월별은 연도에 상관없이 그 달에 찍힌 모든 사진을 모아 보여줍니다. 상세 화면을
  열면 촬영일(연도+월+일)별로 묶여서 나오고, 여러 연도에 걸쳐 있으면 위쪽에 연도 필터 칩이
  나타나 한 연도만 골라 볼 수 있습니다.
- **달력으로 보기**: 연/월을 골라 그 달 달력에서 날짜 칸을 눌러 그날 찍은 사진을 바로
  확인합니다.
- **장소별 정리**: 나라별(해외)과 지역별(국내) 중 골라서 봅니다. 나라별은 사진의 EXIF GPS
  좌표로 해외에서 찍은 사진만 나라 단위로 모아 보여주고(국내 사진은 제외), 나라를 열면
  촬영일자별로 사진이 묶여 나옵니다. 지역별은 국내 여행 사진을 다녀온 장소와 날짜로
  묶어(여행 단위 자동 그룹핑) 보여줍니다.
- **오른쪽 위 연도 아이콘(날짜별 정리 화면)**: 날짜를 직접 골라보거나, 연도 하나를 골라 그
  해에 찍힌 모든 사진을 날짜별로 쭉 훑어보며 정리할 수 있습니다.
- **선택한 사진 삭제/공유/이동**: 어느 상세 화면에서든 사진마다 항상 떠 있는 체크박스를
  탭해서(또는 섹션 헤더에서 그 섹션 전체를) 고른 뒤, 화면 아래 고정된 버튼으로 한 번에
  삭제하거나 다른 앱으로 공유하거나 갤러리의 다른 폴더(앨범)로 옮길 수 있습니다. 사진
  뷰어에서도 보고 있는 사진을 바로 삭제/공유/이동하거나 상세 정보(파일명·촬영일·크기·
  해상도·위치 등)를 볼 수 있습니다. "삭제"는 확인창을 거친 뒤 바로 완전삭제되지 않고
  휴지통으로 이동합니다.
- **휴지통**(홈 화면 오른쪽 위 아이콘): 삭제한 사진이 모이는 곳으로, 항목마다 완전삭제까지
  남은 일수가 표시됩니다. 선택한 사진을 복원하거나 완전삭제할 수 있고, 툴바의 "휴지통 비우기"로
  전체를 한 번에 완전삭제할 수도 있습니다. 휴지통에 보관된 지 14일이 지난 사진은 자동으로
  완전삭제됩니다(자세한 동작은 아래 기술 스택 참고).
- 하루라도 정리(삭제/이동)하면 연속일수(스트릭)가 올라가고, 정리 화면 상단에 표시됩니다.

## 기술 스택

- Kotlin, MediaStore(`MediaStore.Images` + `MediaStore.Video`)로 기기 갤러리의 사진·동영상을
  촬영일자(EXIF `DATE_TAKEN`) 기준으로 읽어옵니다. 날짜별 정리는 연도·월 구분 없이 며칠
  (`dayOfMonth`) 기준으로, 월별 정리는 연도 구분 없이 월 기준으로 그룹핑합니다.
- 장소별(나라별) 보기는 `ACCESS_MEDIA_LOCATION` 권한 + `MediaStore.setRequireOriginal` +
  androidx `ExifInterface`로 원본 GPS 좌표를 읽고, 좌표를 약 1도(~111km) 격자로 뭉쳐 격자당
  지오코딩을 한 번만 호출한 뒤 나라 단위로 합칩니다(사진이 많을 때 지오코딩 호출이 너무
  많아지는 걸 막기 위함). 기기 로케일의 국가(집)는 제외합니다.
- "삭제" 버튼은 `MediaStore.createTrashRequest`(Android 11+, API 30)로 시스템 휴지통에
  보내는 것이며(`PhotoTrasher`), 보낸 시각은 `TrashTracker`(SharedPreferences)에 직접
  기록해 둡니다. 기기·제조사마다 다른 MediaStore 자체 만료 기간과 무관하게, 이 앱은 항상
  기록된 시각 기준 14일이 지나면 완전삭제 대상으로 취급합니다. 완전삭제 자체는
  `MediaStore.createDeleteRequest`(`PhotoDeleter`)로 처리합니다(시스템 확인 다이얼로그가
  뜬 뒤 실제 삭제) — 그래서 `minSdk = 30`으로 설정했습니다.
- 완전삭제는 시스템 확인 다이얼로그가 필요해 백그라운드에서 조용히 처리할 수 없습니다.
  대신 `TrashActivity`를 열 때마다 14일이 지난 사진을 자동으로 완전삭제 요청하고, 앱을
  열지 않고 있는 동안에는 `WorkManager` 1일 주기 워커(`TrashPurgeWorker`)가 만료된 사진이
  있으면 "휴지통 정리가 필요해요" 알림만 보내 앱을 열도록 유도합니다.
- AdMob(`com.google.android.gms.ads`)로 홈 화면에 배너 광고를 붙였습니다.
- Coil(이미지·동영상 썸네일 로딩), ViewBinding, Coroutines
- `PhotoViewerActivity`로 넘기는 사진 목록은 static 홀더(빠른 경로) + 인텐트에 담은 사진 ID
  배열(프로세스가 재생성돼도 Repository에서 다시 불러오는 폴백)을 함께 사용합니다.
- 날짜별/월별/나라별/지역별/연도별 상세 화면은 "사진을 날짜 섹션으로 묶어 그리드로 보여주고,
  선택 후 삭제·공유·이동한다"는 골격이 완전히 같아서 `BasePhotoDetailActivity`로
  공통화했습니다. 하위 클래스는 무엇을 불러올지(`loadPhotosByDate`)와 섹션 라벨
  (`sectionLabel`)만 정합니다.
- 폴더 이동은 Android 11+ scoped storage에서 다른 앱이 만든 미디어를 옮겨야 하므로
  `MediaStore.createWriteRequest`로 시스템 동의를 받은 뒤 `RELATIVE_PATH`를 바꿔
  `Pictures/<폴더명>/`으로 옮기는 방식입니다(`PhotoMover`).
- 삭제/공유/이동 버튼은 툴바 메뉴 아이콘이 아니라 화면에 항상 고정된 일반 뷰 버튼입니다 —
  액션바 메뉴 표시 방식은 기기/테마에 따라 달라질 여지가 있어, 항상 같은 자리에 보이는 쪽을
  택했습니다.

## 화면 구조

```
HomeActivity (런처)
├─ DateOrganizeChooserActivity — 날짜별 정리(일별/월별) 선택
│   ├─ MainActivity        — 일별 정리 (며칠 단위, 월 무관)
│   │   └─ DetailActivity      — 특정 며칠의 촬영일별 사진
│   │       └─ YearDetailActivity — 특정 연도의 날짜별 사진
│   └─ MonthActivity       — 월별 정리
│       └─ MonthDetailActivity — 특정 월의 촬영일별 사진
├─ CalendarActivity        — 달력으로 보기
│   └─ DayDetailActivity       — 특정 날짜의 사진
├─ PlaceChooserActivity    — 장소별 정리(나라별/지역별) 선택
│   ├─ LocationActivity        — 나라별(해외) 정리
│   │   └─ LocationDetailActivity — 특정 나라의 날짜별 사진
│   └─ RegionActivity          — 지역별(국내) 정리, 여행 단위 자동 그룹핑
└─ TrashActivity           — 휴지통 (복원/완전삭제/휴지통 비우기)

모든 상세 화면(DetailActivity/MonthDetailActivity/LocationDetailActivity/YearDetailActivity 등)은
공통으로 PhotoViewerActivity(사진 뷰어)로 이어집니다.
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

CI(GitHub Actions, `.github/workflows/build.yml`)가 main에 push될 때마다 유닛 테스트 + 디버그
APK 빌드를 자동으로 돌리고, `app/build.gradle.kts`의 versionCode를 읽어 `v<versionCode>` 태그로
GitHub Release를 만들어 APK를 첨부합니다(사이드로딩/베타 배포용 — Play Store 정식 릴리스와는
무관). 서명은 저장소에 커밋된 고정 디버그 키(`app/debug.keystore`)를 써서, 어디서 빌드하든
항상 같은 서명이 나와 기기에 이미 설치된 앱 위에 덮어 설치할 수 있습니다.

### Play Store용 릴리스 빌드

Play Store에 올리는 서명된 릴리스(APK/AAB)는 디버그 키가 아니라 별도의 업로드 키를 씁니다.
`keystore.properties.example`을 참고해 로컬에 `keystore.properties`(git에 커밋하지 않음)를
만들고, keytool로 직접 생성한 release keystore 경로/비밀번호를 채운 뒤:

```
./gradlew bundleRelease   # Play Console 업로드용 .aab
./gradlew assembleRelease # 서명된 .apk (필요할 때만)
```

`keystore.properties`가 없으면 release 서명 없이(디버그 빌드는 그대로) 넘어갑니다.

## 알아두면 좋은 점

- `minSdk 30`(Android 11) 이상 기기를 대상으로 합니다. 그 이전 버전은 삭제 권한 처리 방식이
  복잡해져서(레거시 storage 예외 처리) 이번 범위에서는 제외했습니다.
- 사진 "삭제"는 바로 영구 삭제되지 않고 휴지통으로 이동합니다. 휴지통·완전삭제·복원 모두
  단계마다 안드로이드 시스템이 자체 확인 다이얼로그를 띄웁니다.
- 완전삭제(휴지통 비우기 포함)는 되돌릴 수 없습니다.
