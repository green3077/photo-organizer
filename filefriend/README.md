# 파일주고받기 (FileFriend)

가족끼리 3자리 코드로 사진·파일을 주고받는 안드로이드 앱입니다. `photo-organizer`와는
완전히 별개의 독립 앱(별도 Gradle 프로젝트)입니다.

## 사용 방법

- 홈 화면에서 **파일 보내기**를 누르면 바로 안드로이드 "내 파일" 앱(문서 선택기)이 열립니다.
  파일을 고르면 자동으로 업로드되고, 완료되면 3자리 코드가 화면에 크게 표시됩니다
  (30분간 유효). 코드는 복사하거나 문자/카톡 등으로 바로 공유할 수 있습니다.
- 홈 화면에서 **파일 받기**를 누르고 전달받은 3자리 코드를 입력하면 파일이 기기의
  `Download/downloadfriend` 폴더에 저장됩니다. 코드는 한 번 받으면 즉시 소멸되는
  1회용입니다.

## 동작 원리

인터넷 어디서든(같은 와이파이가 아니어도) 주고받을 수 있어야 해서, 파일을 잠깐
보관하는 중계 서버로 **Firebase**(Firestore + Cloud Storage)를 사용합니다.

- 보낼 때: 선택한 파일을 Firebase Storage(`shares/{code}/{파일명}`)에 올리고,
  Firestore(`shares/{code}` 문서)에 파일명·크기·다운로드 주소·만료 시각을 저장합니다.
- 받을 때: 입력한 코드로 Firestore 문서를 조회해 만료 여부를 확인한 뒤, Storage에서
  파일을 내려받아 저장하고, 다 받으면 해당 코드의 Firestore 문서와 Storage 파일을
  바로 삭제합니다(1회용 + 자동 정리).
- 로그인 화면 없이 기기마다 Firebase 익명 인증만 자동으로 붙여서, Firestore/Storage
  보안 규칙이 완전히 열려 있지 않도록(`request.auth != null`) 최소한의 보호만 걸었습니다.

### 알려진 한계

코드가 숫자 3자리(000~999)뿐이라 대규모/공개 배포에는 적합하지 않습니다. 가족 등
소수 인원이 이 앱을 설치해 쓰는 용도로 설계했습니다. 코드 유효시간(30분)이 지나면
자동 만료되어 정리되지만, 그 사이에는 코드를 아는(또는 추측한) 사람이라면 누구나
접근할 수 있습니다.

## Firebase 프로젝트 연결 (최초 1회 설정 필요)

이 저장소에는 실제 Firebase 설정 파일(`google-services.json`)이 포함되어 있지
않습니다(개인 프로젝트 정보라 커밋하지 않음). 빌드/실행 전에 아래 과정을 먼저
해주세요.

1. [Firebase 콘솔](https://console.firebase.google.com/)에서 새 프로젝트를 만듭니다
   (무료 Spark 요금제로 충분합니다).
2. 프로젝트 안에서 **Firestore Database**를 만들고(테스트 모드로 시작해도 됨),
   **Storage**도 활성화합니다.
3. Firestore와 Storage의 "규칙" 탭에 이 저장소의 [`firestore.rules`](firestore.rules),
   [`storage.rules`](storage.rules) 내용을 각각 붙여넣고 게시합니다.
4. **Authentication** 메뉴에서 로그인 방법 중 **익명(Anonymous)**을 사용 설정합니다.
5. 프로젝트 설정 > 내 앱에서 안드로이드 앱을 추가합니다. 패키지 이름은 반드시
   `com.green3077.filefriend`로 입력합니다.
6. 다운로드한 `google-services.json` 파일을 `filefriend/app/google-services.json`
   경로에 그대로 저장합니다(참고용 예시 파일이 `google-services.json.example`로
   같은 위치에 있습니다).

파일을 넣고 나면 별도 설정 없이 바로 빌드/실행할 수 있습니다.

## 빌드

```
cd filefriend
./gradlew :app:assembleDebug
```
