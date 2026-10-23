# #1 로컬 기술 확인

상태: 완료 (2026-10-23)

> 2026-10-23에 GitHub 이슈 #1에서 이 파일로 옮겼다. 원본 이슈는 삭제했다.

## 목표

`RESEARCH.md` 10장의 "기술 확인" 항목을 공식 문서로 확인하고, 필요한 것은 로컬에서 직접 확인한다.

## 체크리스트

- [x] 문서 확인 4건 (`RESEARCH.md` 10.1절)
- [x] 문서와 다른 점 D1~D6 연구자 결정
- [x] 로컬 확인: MVC suspend 컨트롤러가 Unconfined로 시작해 IO 스레드에서 재개되는지 (스레드 이름 로그)
- [x] 로컬 확인: `Dispatchers.IO.limitedParallelism(n)`이 n > 64에서 실제로 n개까지 병렬 실행되는지
- [x] HikariCP 기본값(풀 10, connectionTimeout 30초) 확인
- [x] 코어 분리(cpuset), arm64 이미지 지원 확인 (문서 확인까지. Pi에서의 실제 적용은 #4로 넘겼다)

## 핸드오프

### 핸드오프 (2026-10-23)
- 한 일: 기술 확인 4건(MVC suspend, 가상 스레드 버전별 지원, Dispatchers.IO/limitedParallelism, k6 arrival-rate/dropped_iterations)을 공식 문서로 확인하고 RESEARCH.md 10.1절에 출처와 함께 기록했다(v0.3.1). 구현 이슈 #1~#6을 만들었다. 코드는 아직 없다.
- 남은 일: 문서와 다른 점 D1~D6 결정, 로컬 확인 2건(suspend 실행 스레드, limitedParallelism > 64), HikariCP 기본값, 코어 분리/arm64 확인
- 막힌 점 / 확인 필요: D1(dropped_iterations 해석: SUT 포화로도 생김), D2(limitedParallelism 뷰는 IO의 64 상한에 묶이지 않음), D3(코루틴 프로파일은 MVC 비동기 경유), D4(Boot 3.2 가정 → 현재 4.1.1), D5(두 번째 JDK는 24 이상, 후보 25), D6(HikariCP 7.0.2 vs 7.1.0 가상 스레드 수정). 저장소에 아직 커밋이 없다.
- 다음 세션 첫 작업: D1~D6 결정을 RESEARCH.md에 반영한 뒤, 로컬 확인 2건을 최소 코드로 확인

### 핸드오프 (2026-10-23, 추가)
- 한 일: D1~D6 결정을 RESEARCH.md v0.4에 반영했다. D1 maxVUs를 도착률×요청 타임아웃 이상으로 두고 드롭 원인은 생성기 CPU와 함께 기록, D2 limitedParallelism(n)+기본 IO(64), D3 처리 모델 차이로 인정, D4 Boot 4.1.1, D5 JDK 21/25, D6 HikariCP 7.0.2. 6.6 정의와 임계값은 바꾸지 않았다.
- 남은 일: 로컬 확인 3건(suspend 실행 스레드, limitedParallelism n>64, JDK 25 LTS 여부), HikariCP 기본값, 코어 분리/arm64
- 막힌 점 / 확인 필요: JDK 25 LTS 여부는 공식 문서로 아직 확인하지 못했다. D1~D6 반영분은 아직 커밋하지 않았다.
- 다음 세션 첫 작업: 로컬 확인 3건 진행 후 RESEARCH.md 3장과 H3의 64 가정 수정

### 핸드오프 (2026-10-23, 로컬 확인)
- 한 일: 로컬 확인 2건을 스크래치패드의 일회성 코드로 진행했다(저장소에는 넣지 않음, 맥북 M1, JDK 21). (1) MVC suspend 컨트롤러는 톰캣 스레드에서 시작해 Dispatchers.IO 스레드에서 재개되고, REQUEST와 ASYNC 디스패치가 한 번씩 일어났다. 가상 스레드 설정을 켜도 IO 작업은 플랫폼 스레드에서 돌았다. (2) Dispatchers.IO.limitedParallelism(n)은 n=128, 200에서 실제로 n개까지 동시 실행되었다. HikariCP 기본값, Compose cpuset 형식, arm64 이미지 제공을 문서로 확인했다. 빌드 도구는 Gradle Kotlin DSL로 정했다. 모두 RESEARCH.md v0.4.2 10.1절과 7장에 기록했다.
- 남은 일: Pi에서 cpuset이 실제로 적용되는지(#4), PostgreSQL 메이저 버전과 이미지 태그(#3, #4), maxVUs와 요청 타임아웃(#3)
- 막힌 점 / 확인 필요: D1 기준을 connectionTimeout 30초로 계산하면 VU가 매우 커진다(500건/초에서 15,000). 맥북 k6가 감당하는지 #3에서 확인해야 한다. 로컬 확인 반영분은 아직 커밋하지 않았다.
- 다음 세션 첫 작업: #1을 닫고 #2(앱 뼈대) 시작. 새 의존성 목록부터 승인받는다.
