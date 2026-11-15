# 동행 — 공개 Pleos SDK 기반 구현안

검토일: 2026-11-14 (한국 시간)  
상태: 개발 설계. 이번 수정은 문서에 반영했으며 앱 코드·권한·APK는 변경하지 않았다.

## 1. 출품 범위

공식 개발 범위 공지와 사용자의 최종 요청을 기준으로, 이번 출품 구현은 공개된 Pleos SDK·API와 Gleo Interface를 중심으로 구성한다. 앞서 제안한 외부 지도 API와 외부 LLM 서버를 기본 구성에서 제외한다.

공지가 직접 명시하는 내용은 공개된 SDK·API 범위 준수, 미지원 기능의 향후 계획 분리, 외부 음성 인식으로 차량 마이크 전송 불가이다. 이 문장을 모든 인터넷 통신 금지로 확대 해석하지 않는다. 공식 SDK가 제공하는 네트워크 기능은 각 SDK의 사용 조건에 따라 검토한다. [S1]

- 사용 대상: Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation.
- Gleo Interface(App Schema): 공모전 참가팀은 별도 검토·승인 없이 사용 가능.
- 공개되지 않은 SDK와 Pleos LLM SDK: 공모전 기간 중 제공되지 않음.
- 차량 마이크 입력: SpeechToText SDK 사용.
- 자체 지도 서버·외부 LLM·외부 STT: 이번 출품의 필수 기능에 포함하지 않음.

제품 목표는 그대로 유지한다. 동행은 초보 운전자가 자주 다니는 길에서 어려웠던 구간을 기억하고, 다음 주행에서 필요한 도움을 선택해 반복 연습하는 앱이다.

## 2. 현재 보이는 지도와 내비 기능

서로 다른 화면을 구분해야 한다.

| 대상 | 실제 상태 |
|---|---|
| Pleos 기본 내비의 지도 | 플랫폼의 내비게이션 앱이 제공하는 화면. NaviHelper로 목적지·경로 및 화면 표시 요청을 연동할 수 있음. [S2] |
| 동행 출발 준비의 경로 그림 | CoachCharts.kt의 RouteSketch가 Canvas로 그리는 예시 도식. 실제 지도 타일·좌표 기반 지도가 아님. 화면에도 이를 표시함. |
| 동행의 내비 데이터 수신 | LiveTelemetry에서 getCurrentLocationInfo, getRouteStateInfo, getTBTInfo를 호출하고 콜백 처리 중. |
| 동행 안의 실제 지도 삽입 | 현재 미구현. 기본 내비 화면을 요청할 수 있다는 사실을 임의 MapView 삽입·마커 오버레이 지원으로 간주하지 않음. |

따라서 내비 기능의 부족을 가정해 외부 지도를 먼저 도입할 필요는 없다. 기본 내비를 경로의 기준으로 사용하고, 동행은 구간 학습과 기록에 집중한다.

## 3. 활용할 공식 기능과 현재 연결 상태

| 목적 | 공개 기능 | 동행에서 할 작업 |
|---|---|---|
| 현재 경로의 위치·회전 정보 | NaviHelper의 위치·경로 상태·TBT 조회 | 기존 수신과 위치·방향·경로 변경 검증 보완 |
| 목적지·경유지 설정 | requestRoute, requestRouteWithWaypoints | 주차 상태에서 요청하고 경로 탐색 완료 콜백 확인 |
| 최근 목적지·즐겨찾기 | getRecentDestinationInfo, getBookmarkInfo | 입력을 줄이는 출발 준비 화면 검토 |
| 경로 옵션 | changeRouteOption | 공식 옵션 중 필요한 것만 제공. COMFORTABLE을 초보자 안전 보장으로 표현하지 않음 |
| 기본 내비 화면 표시 | requestNavigationScreen | 목적지 검색·전체 경로 등 필요한 화면 요청을 에뮬레이터에서 검증 |
| 차량 조작 기록 | Vehicle의 브레이크·방향지시등, 문서 범위의 AAOS 속도·주차 상태 | 기존 연결 유지, 값의 지원 여부·최신성 확인 |
| 위치 보완 | FusedLocation | isSupported와 권한·오류·위치 차단 상태 검증 후 필요할 때 적용 |
| 음성 안내·소감 | Pleos TTS / STT | ON_DEVICE부터 검증; 서버 모드는 공식 등록 조건을 충족한 뒤 검토 |
| 음성 기능 호출 | Gleo App Schema | “지난 연습 보기”, “연습 준비”처럼 제한된 기능부터 연결 |

NaviHelper는 기본 내비에 요청을 전달하고 이벤트로 결과를 받는 구조이다. 호출 성공만으로 경로 탐색·화면 표시 성공을 판정하지 않는다. [S2, S3]

권한은 기능별로 나뉜다. 현재 앱은 NAVI_ROUTE를 선언했다. 목적지·경로 변경에는 NAVI_ROUTE_SEARCH, 내비 화면 요청에는 NAVI_CUSTOM_MAP이 문서화되어 있다. 실제 기능을 추가할 때 필요한 권한만 선언하고 사용 조건·실제 동작을 검증한다. 이 문서 수정에서 권한을 추가하지 않았다. [S4]

## 4. 경로 등록을 줄이는 구현 순서

1. 현재 기본 내비의 목적지·경로 상태를 읽어 동행의 연습 세션과 연결한다.
2. 고정된 두 구간을 RouteDefinition과 RouteRepository로 일반화한다.
3. 검증된 한 경로에서 위치·진입 방향·TBT 변화와 구간 기록을 연결한다.
4. 첫 주행 후 사용자가 복습에서 어려웠던 구간을 선택하도록 한다.
5. 다음 주행에서 같은 구간·같은 접근 방향인지 확인하고 선택한 목표에 따라 안내한다.
6. 경로 변경·재탐색 시 이전 세션의 구간 연결을 무효화한다.

출발 전 전체 경로의 모든 교차로를 자동 추출하는 기능은 공개 데이터로 가능한지 검증한 뒤 범위를 정한다. 현재 사용하는 TBTInfo의 공개 getter는 회전 종류·거리·설명이며, 이것만으로 전체 경로의 교차로 좌표열을 확보했다고 주장할 수 없다.

설치된 NaviHelper 3.0.5 AAR과 조사용 classes.jar가 동일함을 확인했고, requestNavigationScreen, requestTrip, requestRouteWithWaypoints 메서드가 존재함을 확인했다. 별도 data classes.jar에서는 TripInfo의 거리·ETA·에너지·요약 getter와 TBTInfo의 위 세 getter를 확인했다. 이는 API 존재 확인이며 실제 에뮬레이터 응답 검증은 아니다.

초기 시연에는 검증한 경로와 구간을 사용한다. 첫 주행에서 구간을 자동 발견·저장하는 기능은 별도 개발 과제로 둔다. API가 있다는 이유로 현재의 수동 경로 보정을 제거하거나 calibrated=true를 자동 지정하지 않는다.

현재 앱은 배경으로 이동하면 기록을 종료한다. 내비 화면을 요청하는 기능은 우선 출발 준비에서만 사용하고, 주행 중 앱 전환이 필요한 동작은 수명주기와 주행 제한 정책을 함께 검증한다. 주행 중 내비와 동행이 동시에 계속 실행된다고 가정하지 않는다.

## 5. 외부 LLM 없이 개인별 복습 구현

앱에서 관측 사실을 계산하고 사용자가 고른 어려움과 결합해 정형 문장을 만든다. 목표는 기존 ROUTE_PREVIEW, SIGNAL_CHECK, SPEED_REVIEW 중 하나를 제안하고 사용자가 결정한다.

예시: “이번 기록에서는 선택한 구간에 진입했을 때 우측 방향지시등이 켜져 있었습니다.” 해당 사실이 관측되었을 때만 표시한다.

- 브레이크는 눌림 여부이다. 압력·제동 강도·급제동 원인을 추정하지 않는다.
- 속도 기록은 관측값으로 보여준다. 적정 회전 속도·종합 안전 점수를 만들지 않는다.
- 현재 샘플은 구간 진입부터 저장된다. 진입 전 신호 시점을 분석하려면 순환 버퍼를 추가해야 한다.
- 관측 첫 프레임에 이미 신호가 켜져 있었다면 켜진 시각은 알 수 없다.
- 현재 “구간 진입 0초”를 실제 회전 시각처럼 해석하지 않는다.
- 누락·만료·경로 불일치 자료는 분석에서 제외하고 관측 범위를 표시한다.
- 동일 출처·구간·접근 방향끼리 비교한다. 두 기록으로 장기 실력 향상을 단정하지 않는다.

Gleo Interface는 앱이 정의한 기능을 음성으로 호출하고 결과를 돌려주는 연동 방식이다. 임의 프롬프트를 보내는 범용 LLM SDK로 취급하지 않는다. 앱이 계산한 복습 요약을 반환하는 기능을 정의할 수 있는지 검증하되, 사용자에게 표시할 사실은 앱의 분석 결과로 유지한다. [S5]

주행 안내는 로컬 엔진의 짧은 문구를 사용한다. 주차·조작 허용·중복 호출·세션 상태를 앱에서 검사하며 음성 호출도 같은 제한을 적용한다. 오디오 포커스 상실 뒤 지난 구간의 안내를 늦게 재생하지 않는다.

## 6. 데이터 흐름

~~~mermaid
flowchart TD
    A["기본 내비·NaviHelper"] --> C["경로·구간 확인"]
    B["Vehicle·지원되는 차량 상태"] --> D["관측값과 최신성 검사"]
    C --> E["로컬 안내·기록"]
    D --> E
    E --> F["주차 후 분석·복습"]
    F --> G["다음 연습 목표"]
    G --> E
    H["Gleo 음성 명령"] --> I["앱의 상태·조작 허용 검사"]
    I --> G
~~~

필수 서버 없이 시작한다. 원본 기록과 설정은 차량의 앱 저장소에서 처리한다. 개인정보가 포함되는 저장 데이터의 보호와 로그 정리는 별도 보완 과제이며, 현재 평문 SharedPreferences를 암호화 구현으로 표현하지 않는다. [S8]

## 7. 코드 변경 계획과 완료 기준

| 순서 | 변경 | 완료 기준 |
|---|---|---|
| 0 | 현재 시연 안정화 | 최신 APK에서 기록→복습→설정→반복 실행. 동적 SDK 수신과 합성 재생 구분 |
| 1 | Models·CoachEngine의 SampleRoute 의존 제거 | 여러 구간을 동일 엔진으로 처리하고 순서·접근 방향 검증 유지 |
| 2 | StateCodec·LocalStore 스키마 v2 | 기존 설정·기록 손실 없이 이전, 경로·버전별 기록 혼합 없음 |
| 3 | NaviHelper 활용 확대 | 현재 목적지·즐겨찾기와 검증 경로 1개 연결, 취소·재탐색 무효화 |
| 4 | 로컬 사건 요약과 맞춤 복습 | 데이터 누락·이미 켜진 신호·관측 경계 처리, 근거 있는 문장만 생성 |
| 5 | 공식 TTS·STT와 Gleo | 사용 조건·권한, P 기어 제한, 오디오 충돌·앱 전환·중복 명령 검증 |

기존 21개 테스트 통과와 APK 생성은 이전 검증 기록이다. 이번에는 앱 코드를 변경하지 않았으며 앱 테스트를 다시 실행하지 않았다. 실제 동적 주행 입력, 최신 애니메이션 표시, 내비와 오디오 공존은 여전히 검증 과제이다.

시연은 경로 준비 → 첫 주행 기록 → 어려운 구간 선택 → 목표 저장 → 반복 주행에서 안내 또는 생략 → 복습으로 구성한다. 인터넷이 끊겼을 때 로컬 기능이 유지되는 것과, 내비·위치 데이터가 오래되어 안내를 보류하는 것을 구분한다.

## 8. 제출 시 구분할 사항

외부 지도·외부 LLM 연동은 현재 출품 기능으로 적지 않는다. 향후 확장으로 남길 경우에도 공모전 구현 범위와 구분한다.

공식 가이드상 실제 차량이 필수는 아니며 에뮬레이터 시연으로 진행한다. 현재 알려진 APK는 debug 빌드이며 제출 가이드는 실행 가능한 release APK를 안내한다. 로컬 변경분·소스·APK·최종 커밋의 일치를 제출 전에 확인해야 한다. [S1, S9]

사용자의 비공개 저장소 선호를 유지하려면 소스·APK 압축파일 다운로드 주소 제출 대안을 검토한다. GitHub 방식의 공개 전환 일정은 공식 제출 안내를 따른다. 이번 작업에서는 공개 범위·배포·메일 전송을 변경하지 않았다. [S10]

## 참고 자료

- [S1 공모전 개발 범위 및 기술 문의](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008) — 사용자가 본문을 제공했고 앞선 검토에서도 열람한 공지.
- [S2 NaviHelper 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)
- [S3 경유지 포함 경로 요청](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints)
- [S4 NaviHelper 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [S5 Gleo Interface](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/core-concepts)
- [S6 Pleos TextToSpeech](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)
- [S7 Pleos SpeechToText](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)
- [S8 App Security Guide](https://document.pleos.ai/en/docs/pleos-only/app-registration-guide/app-security-guide)
- [S9 지원·제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission)
- [S10 비공개 GitHub·접수 추가 안내](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000007)
- [S11 FusedLocation 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)
- [S12 내비 경로 옵션](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/routes)
