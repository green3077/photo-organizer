# Play 스토어 등록 체크리스트

이 문서는 "사진정리"를 Google Play에 올리는 데 필요한 전체 절차를 정리한 것입니다.
코드/서명/스토어 자료 준비는 끝났고, 아래 굵게 표시된 항목은 본인 Google 계정으로
직접 진행해야 하는 단계입니다(결제·신원 인증·약관 동의가 들어가서 대신 해줄 수 없음).

## 1. 준비 완료된 것

- [x] 앱 내 자체 업데이트(GitHub Releases APK 직접 설치) 기능 제거 — Play 정책 위반 방지
- [x] release 빌드 서명 설정 (`keystore.properties`, 커밋 안 됨) + minify/shrinkResources
- [x] 업로드용 서명키 생성 완료: `C:\Users\hkjin\keys\photo-organizer-release.jks`
      (비밀번호는 `keystore.properties`에 있음 — **반드시 별도 백업**하세요. 분실 시
      Play Console에 업로드 키 재설정을 요청해야 하는 번거로움이 생깁니다.)
- [x] 서명된 릴리스 AAB 빌드 확인 완료: `app/build/outputs/bundle/release/app-release.aab`
      (versionCode 17, versionName 1.16 — 코드가 바뀌면 `./gradlew bundleRelease`로 재빌드)
- [x] 개인정보처리방침 페이지 게시: https://green3077.github.io/photo-organizer/privacy-policy.html
      (GitHub Pages 방금 활성화함 — 반영까지 몇 분 걸릴 수 있음)
- [x] 스토어 등록 문구 초안: `docs/store/listing-ko.md`
- [x] 고해상도 아이콘(512x512), 그래픽 이미지(1024x500): `docs/store/`

## 2. 본인이 직접 해야 하는 것

1. **Play Console 개발자 계정 생성** — https://play.google.com/console 접속 →
   green3077 Google 계정으로 등록비($25) 결제 + 신원 인증. 심사에 며칠 걸릴 수 있음.
2. **새 앱 만들기** — 앱 이름 "사진정리", 기본 언어 한국어, 앱/게임: 앱, 무료/유료: 무료.
3. **스토어 등록정보 입력** — `docs/store/listing-ko.md` 내용을 그대로 붙여넣고,
   `docs/store/ic_launcher_512.png`(아이콘), `docs/store/feature_graphic.png`(그래픽 이미지)를
   업로드.
4. **스크린샷 준비 (최소 2장, 권장 4장 이상)** — 직접 폰에 앱을 설치해 실제 화면을
   캡처하는 걸 추천합니다(에뮬레이터에 가짜 사진을 채우는 것보다 실제 갤러리 화면이
   훨씬 자연스럽고 빠름). 홈 화면, 날짜별 정리 상세, 장소별 정리 정도면 충분합니다.
5. **데이터 보안(Data safety) 설문** — Play Console이 안내하는 설문에 아래 내용 참고해 답변:
   - 사진·동영상: 기기에서 읽기는 하지만 앱이 외부로 전송/저장하지 않음 → "수집" 여부는
     Play 정책 가이드를 한 번 확인해서 판단하세요(온디바이스 처리만 하는 경우 보통
     "수집 안 함"으로 표시 가능).
   - 광고 식별자(Advertising ID): AdMob 배너 광고 때문에 수집됨 → "예", 목적은
     "광고 또는 마케팅".
   - 위치정보: 사진 EXIF GPS를 안드로이드 시스템 Geocoder(내부적으로 Google 위치 확인
     서비스 경유 가능)에 넘겨 국가/지역 이름으로 변환 → 이 항목은 애매한 부분이라
     Play 고객센터의 데이터 보안 가이드를 한 번 확인하고 답변하는 걸 권장합니다.
   - 사용자 계정/로그인 없음, 서버에 개인정보 저장 안 함.
6. **콘텐츠 등급(IARC) 설문** — 폭력/선정성/도박/약물 등 전부 "해당 없음"으로 답하면
   대부분 "전체이용가"로 나올 것으로 예상됩니다. 실제 문항은 Console에서 직접 확인하세요.
7. **타겟 연령층/광고 관련 선언** — 아동 대상 앱이 아님으로 설정 권장(만 13세 이상
   일반 사용자 대상). 광고 포함 앱으로 표시.
8. **국가/지역 선택** — 앱 UI가 한국어 전용이라 우선 대한민국만 선택하거나, 이후
   다국어 지원 시 확대하는 것을 권장합니다(본인 판단 필요).
9. **프로덕션 트랙에 AAB 업로드** — `app/build/outputs/bundle/release/app-release.aab`
   업로드 (버전 올릴 때마다 `app/build.gradle.kts`의 versionCode/versionName을 올리고
   재빌드).
10. **검토 제출** — 위 항목이 모두 채워지면 "검토를 위해 제출". 첫 심사는 보통
    며칠 걸릴 수 있습니다.

## 3. 참고

- `app/build.gradle.kts`의 `versionCode`를 새로 올릴 때마다 `bundleRelease`로 다시
  빌드해서 새 AAB를 업로드해야 합니다.
- GitHub Releases 배포(사이드로드)는 그대로 유지되며(디버그 서명), Play 배포와는
  독립적인 별개 채널입니다. 두 채널 서명이 다르므로, 한 기기에 GitHub 버전을
  설치한 사용자가 Play 버전으로 바꾸려면 먼저 기존 앱을 삭제해야 합니다.
