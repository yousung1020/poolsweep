# #2 앱 뼈대: 세 프로파일, /query, /ping, 지표

상태: 완료 (2026-10-23)

> 2026-10-23에 GitHub 이슈 #2에서 이 파일로 옮겼다. 원본 이슈는 삭제했다.

## 목표

`RESEARCH.md` 6.1과 13장 2번을 구현한다.

## 체크리스트

- [x] 새 의존성 목록(코루틴 2종, Actuator, Prometheus 레지스트리, JDBC, PostgreSQL 드라이버)을 먼저 보여 주고 승인받는다
- [x] `app/`에 Spring Boot 4.1.1 + Kotlin + Gradle Kotlin DSL(wrapper) 단일 프로젝트를 만든다
- [x] 프로파일 `platform` / `virtual` / `coroutine`을 만든다. 코루틴 프로파일은 가상 스레드 설정을 끈다 (`RESEARCH.md` 10.1 (1))
- [x] 서비스와 레포지토리(JdbcTemplate)는 공유하고, 프로파일별 차이는 컨트롤러와 실행 계층에만 둔다
- [x] `/query`(쿼리 유형 파라미터)와 `/ping`(DB 미호출)을 만든다
- [x] 풀 크기, 처리 모델, IO 병렬도는 환경변수로 주입한다. 쿼리 유형과 id는 요청 파라미터로 받는다(연구자 결정, `RESEARCH.md` 6.1)
- [x] Actuator + Micrometer(Prometheus)로 풀 지표를 노출한다
- [x] 빌드와 실행 명령을 확인해 `CLAUDE.md` 6장에 기록한다

선행 조건이던 #1의 D1~D6 결정은 끝났다.

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
