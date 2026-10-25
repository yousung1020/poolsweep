# #2 앱 뼈대: 세 프로파일, /query, /ping, 지표

- 상태: 완료 (2026-10-23)
- 선행: #1 (완료)
- 후행: #3
- 최종 갱신: 2026-10-25
- 출처: 2026-10-23에 GitHub 이슈 #2에서 이 파일로 옮겼다. 원본 이슈는 삭제했다.

## 목표

세 처리 모델(`platform`, `virtual`, `coroutine`)을 프로파일 하나로 전환하는 앱을 만든다. 공정한 비교를 위해 프로파일 사이의 차이는 요청 처리 방식에만 두고, 실제로 적용된 설정과 풀 지표를 밖에서 읽을 수 있게 한다.

## 완료 기준

- `./gradlew test bootJar`가 성공한다.
- 세 프로파일 모두 `/query`와 `/ping`에 응답하고, `/actuator/info`가 실제 적용 설정을, `/actuator/prometheus`가 풀 지표를 보여 준다.
- 빌드와 실행 명령이 `CLAUDE.md` 6장과 `README.md`에 기록되어 있다.

## 작업 맥락

### 이미 정해진 것
- Spring Boot 4.1.1, HikariCP는 Boot 관리 버전(7.0.2), DB 접근은 `JdbcTemplate`이다. (`RESEARCH.md` 6.1, D4, D6)
- 코루틴 프로파일은 `withContext`로 JDBC를 IO 디스패처에서 호출하고, 가상 스레드 설정을 끈다. (`RESEARCH.md` 6.1, 10.1 (1))
- IO 병렬도는 `default`(`Dispatchers.IO`)와 `limitedParallelism(n)`이다. (`RESEARCH.md` 6.3, D2)
- 쿼리 유형과 id는 요청 파라미터로 받고, 스키마와 시드는 `infra/db/init.sql`이 만든다. (`RESEARCH.md` 6.1, 2026-10-23 연구자 결정)
- `/ping`은 세 프로파일 공통의 일반 핸들러로 둔다. (2026-10-25 연구자 결정, `RESEARCH.md` 6.1)
- 설정 파일은 YAML로 쓴다. (2026-10-25)

### 지켜야 할 규칙
- 공통 코드(레포지토리, 쿼리)는 세 프로파일이 똑같이 쓴다. (`CLAUDE.md` 2장)
- 풀 크기 외의 HikariCP, 톰캣 설정은 기본값으로 고정한다. (`CLAUDE.md` 2장)

### 관련 파일과 명령
- [app/build.gradle.kts](../app/build.gradle.kts): 의존성과 버전이다.
- [app/src/main/resources/](../app/src/main/resources/): 공통 설정과 프로파일별 설정이다.
- [app/src/main/kotlin/poolsweep/](../app/src/main/kotlin/poolsweep/): 쿼리 정의, 프로파일 검사와 설정 노출, 레포지토리, 컨트롤러이다.
- [app/src/test/kotlin/poolsweep/ProcessingModelProfileTest.kt](../app/src/test/kotlin/poolsweep/ProcessingModelProfileTest.kt): 프로파일별 동작 테스트이다.
- [infra/db/init.sql](../infra/db/init.sql): `item` 1만 행 시드이다.
- 명령: `CLAUDE.md` 6장의 "앱" 항목이다.

## 열린 질문

없음

## 체크리스트

- [x] 새 의존성 목록(코루틴 2종, Actuator, Prometheus 레지스트리, JDBC, PostgreSQL 드라이버)을 먼저 보여 주고 승인받는다
- [x] `app/`에 Spring Boot 4.1.1 + Kotlin + Gradle Kotlin DSL(wrapper) 단일 프로젝트를 만든다
- [x] 프로파일 `platform` / `virtual` / `coroutine`을 만든다. 코루틴 프로파일은 가상 스레드 설정을 끈다 (`RESEARCH.md` 10.1 (1))
- [x] 서비스와 레포지토리(JdbcTemplate)는 공유하고, 프로파일별 차이는 컨트롤러와 실행 계층에만 둔다
- [x] `/query`(쿼리 유형 파라미터)와 `/ping`(DB 미호출)을 만든다
- [x] 풀 크기, 처리 모델, IO 병렬도는 환경변수로 주입한다. 쿼리 유형과 id는 요청 파라미터로 받는다(연구자 결정, `RESEARCH.md` 6.1)
- [x] Actuator + Micrometer(Prometheus)로 풀 지표를 노출한다
- [x] 빌드와 실행 명령을 확인해 `CLAUDE.md` 6장에 기록한다

## 결과

- 산출물: `app/` 전체, `infra/db/init.sql`, `README.md`이다.
- 마지막 커밋: `cec02f6` (feat: 세 처리 모델 프로파일의 앱 뼈대와 DB 시드 스크립트 추가)
- 승격한 결정: 쿼리 유형, id 전달, 스키마와 시드(`RESEARCH.md` 6.1, v0.5)
- 넘긴 일: k6가 시드 범위의 id만 보내야 한다는 조건과 PostgreSQL 버전 선택은 #3으로 넘겼다.

## 핸드오프

### 핸드오프 (2026-10-23)
- 한 일:
  - `app/`에 Boot 4.1.1 + Kotlin + Gradle Kotlin DSL 앱을 만들었다. 처리 모델 프로파일 세 개와 `/query`, `/ping`, `/actuator/info`(실제 적용 설정), `/actuator/prometheus`(풀 지표)가 있다.
  - DB 접근은 공통 `ItemRepository` 하나다. platform과 virtual은 같은 블로킹 컨트롤러를 쓰고, coroutine은 suspend 컨트롤러에서 IO 디스패처로 JDBC를 호출한다.
  - 처리 모델 프로파일이 정확히 하나가 아니거나 `POOL_SIZE`가 없으면 기동이 실패한다.
  - `infra/db/init.sql`에 `item` 1만 행 시드를 만들었다.
  - 테스트 6개가 모두 통과했다(DB 없이). 실제 DB 확인은 맥북 로컬의 임시 `postgres:17`(17.11) 컨테이너로 세 프로파일 모두 `/query` 동작을 확인했고, 컨테이너는 지웠다. 측정이 아니라 동작 확인이다.
- 남은 일: 없음. 로컬 Compose와 k6는 #3이다.
- 막힌 점 / 확인 필요:
  - `pg_sleep`은 id가 없으면 실행되지 않는다(EXPLAIN ANALYZE). k6는 시드 범위의 id만 보내야 한다(#3에 추가).
  - `postgres:18.6-bookworm` 이미지 다운로드가 멈춰서 확인에는 로컬에 있던 `postgres:17`을 썼다. PostgreSQL 버전 결정은 #3이다.
- 다음 세션 첫 작업: #3 로컬 Compose + k6 최소 스크립트

### 핸드오프 (2026-10-25)
- 한 일:
  - 설정 파일 네 개를 `.properties`에서 YAML로 바꿨다(`app/src/main/resources/application*.yaml`). 키와 값은 그대로이다.
    - 역할과 기능: 공통 파일은 DB, 풀 크기, Actuator 노출을, 프로파일 파일은 가상 스레드 설정과 IO 병렬도를 정한다.
    - 쓰이는 곳: 앱 기동 시 읽는다. 실행 스크립트(#5)가 환경변수로 값을 넣을 예정이다(미연결).
    - 없으면 안 되는 이유: `.properties`를 남겨 두면 같은 위치에서 `.properties`가 우선해 YAML 수정이 반영되지 않는다.
    - 전/후 비교: 전에는 `application*.properties` 네 개였고, 지금은 `application*.yaml` 네 개만 있다.
  - 연구자 결정으로 `/ping`을 세 프로파일 공통으로 확정하고, #2 작업분을 `cec02f6`으로 커밋했다(푸시는 연구자가 했다).
  - 이슈 파일을 `docs/WRITING.md`의 구조로 바꿨다.
- 남은 일: 없음
- 막힌 점 / 확인 필요: GitHub 공개 저장소의 파일 수는 `.properties`가 YAML의 약 2배였다(2026-10-25, 코드 검색 근사치). YAML을 고른 이유는 사용 비율이 아니라 계층이 깊은 설정의 가독성이다.
- 다음 세션 첫 작업: #3 로컬 Compose + k6 최소 스크립트
