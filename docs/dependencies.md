# 의존성과 에셋

Kotlin/Jetpack Compose/AndroidX, Gradle, Temurin OpenJDK 및 Pleos SDK를 사용합니다. 각 도구와 라이브러리는 해당 프로젝트의 라이선스에 따릅니다. 소스 ZIP에는 IDE·SDK·Pleos 시스템 이미지·Gradle 캐시를 포함하지 않습니다. APK에는 앱 실행에 필요한 의존성이 포함됩니다.

앱의 네 음성 파일은 고정된 한국어 문장을 합성한 프로토타입용 WAV입니다. 제작 시 Google의 한국어 음성 합성 응답을 WAV로 변환하고 앞에 짧은 여유 구간을 추가했습니다. 사람의 녹음 또는 Pleos TTS 출력이 아닙니다. 앱 실행 중 외부 음성 서비스에 연결하지 않습니다. 실제 배포 시 배포 범위에 맞는 음성 에셋으로 교체하고 이용 조건을 확인하세요.

Vehicle SDK는 Maven에서 내려받을 수 있는 2.3.2를 사용했습니다. 조사 시 문서의 2.3.1은 해당 저장소에서 내려받을 수 없었습니다. NaviHelper는 3.0.5입니다. 실제 사용 API는 내려받은 AAR과 컴파일 결과를 확인했습니다.

도구 배포 무결성:

- Temurin JDK 17.0.20.1+1 SHA-256: e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0
- Gradle 8.10.2 SHA-256: 31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26
- Android Studio Rabbit 1 ZIP SHA-256: 6e83e7ce3e0a76c4e8e78d98eb5ef538cbc9fb386c745bda425fc0bacb9de6d4
- Pleos 2.1.0 공식 이미지 SHA-1: b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa
