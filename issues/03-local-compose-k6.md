# #3 로컬 Compose + k6 최소 스크립트

- 상태: 대기
- 선행: #2 (완료)
- 후행: #4, #5
- 최종 갱신: 2026-10-25
- 출처: 2026-10-23에 GitHub 이슈 #3에서 이 파일로 옮겼다. 원본 이슈는 삭제했다.

## 목표

맥북에서 PostgreSQL과 앱을 명령 하나로 띄우고, k6 열린 모델(E1) 최소 스크립트로 `/query`에 부하를 걸 수 있게 한다. Pi 배포(#4)와 실행 자동화(#5) 전에 부하 생성 방식과 VU 설정이 결과를 오염시키지 않는지 로컬에서 먼저 확인하려는 것이다.

## 완료 기준

- 로컬 Compose로 PostgreSQL과 앱을 기동하고 종료하는 명령이 `CLAUDE.md` 6장과 `README.md`에 기록되어 있다.
- 모든 이미지 태그가 고정되어 있다(`latest` 없음).
- k6 E1 스크립트가 고정 도착률로 `/query`를 호출하고, `dropped_iterations`와 `/ping` 기준선을 결과에 남긴다.
- `maxVUs`와 요청 타임아웃의 값과 근거가 `RESEARCH.md`에 기록되어 있다.
- k6 실행 명령과 k6 버전이 `CLAUDE.md` 6장과 `README.md`에 기록되어 있다.

## 작업 맥락

### 이미 정해진 것
- 주 실험(E1)은 열린 모델이며 `constant-arrival-rate` 또는 `ramping-arrival-rate`를 쓴다. 지연은 k6에서 잰다. (`RESEARCH.md` 6.4, 7장)
- `maxVUs`는 도착률 × 요청 타임아웃 이상으로 잡는다. 드롭이 남으면 원인을 단정하지 않고 생성기 CPU와 함께 기록한다. (`RESEARCH.md` 6.5, D1)
- k6의 `maxVUs`는 설정하지 않으면 `preAllocatedVUs`와 같고, `preAllocatedVUs`는 필수이다. (`RESEARCH.md` 10.1 (4))
- HikariCP `connectionTimeout`은 기본값 30초로 고정되어 있다. 이 값으로 D1을 계산하면 500건/초에서 15,000 VU가 된다. (`RESEARCH.md` 10.1 (5))
- 요청은 `/query?type=pk|sleep10|sleep50&id=N`이다. id는 k6가 시드 범위 1~10000에서 고른다. 없는 id면 `pg_sleep`이 실행되지 않아 지연 쿼리가 짧아진다. k6 시드를 고정하면 요청 순서도 재현된다. (`RESEARCH.md` 6.1)
- `/ping`은 DB를 호출하지 않는 공통 핸들러이며, 네트워크와 서버 기본 지연의 기준선이다. (`RESEARCH.md` 6.1, 6.2)
- 스키마와 시드는 PostgreSQL 컨테이너가 `infra/db/init.sql`을 `/docker-entrypoint-initdb.d/`에서 실행해 만든다. 앱은 스키마를 만들지 않는다. (`RESEARCH.md` 6.1)
- 앱은 `SPRING_PROFILES_ACTIVE`, `POOL_SIZE`, `DB_URL`, `DB_USER`, `DB_PASSWORD`(코루틴은 `IO_PARALLELISM`)를 환경변수로 받는다. 처리 모델 프로파일이 하나가 아니거나 `POOL_SIZE`가 없으면 기동이 실패한다. (`CLAUDE.md` 6장)
- PostgreSQL 공식 이미지와 eclipse-temurin 이미지는 arm64를 제공한다. 확인 시점의 PostgreSQL 최신 패치는 18.6과 17.11이다. (`RESEARCH.md` 10.1 (6))
- 실행 JDK는 21과 25를 비교한다. (`RESEARCH.md` 6.3, D5)

### 지켜야 할 규칙
- 로컬에서 얻은 숫자는 `results/`에 넣지 않는다. 동작 확인용 숫자임을 밝힌다. (`CLAUDE.md` 3장)
- 인프라 명령은 확인한 뒤 `CLAUDE.md` 6장에 기록하고, 기록 전에는 추측해서 실행하지 않는다. (`CLAUDE.md` 6장)
- 이미지 태그와 도구 버전을 고정하고, 새 실행 방법은 `README.md`에도 쓴다. (`CLAUDE.md` 4장)
- PostgreSQL과 톰캣 설정은 기본값으로 둔다. 바꿔야 할 것 같으면 먼저 묻는다. (`CLAUDE.md` 2장)

### 관련 파일과 명령
- [infra/db/init.sql](../infra/db/init.sql): PostgreSQL 컨테이너가 실행할 시드 스크립트이다.
- [app/src/main/resources/application.yaml](../app/src/main/resources/application.yaml): 앱이 받는 환경변수가 정의되어 있다.
- [README.md](../README.md): 앱 빌드와 실행 방법이 있다. 이번에 인프라와 부하 실행 절을 더한다.
- 앱 빌드와 실행 명령은 `CLAUDE.md` 6장의 "앱" 항목이다.
- 로컬 Compose 파일(`infra/` 아래)과 k6 E1 스크립트(`load/` 아래)는 만들 예정이다.

## 열린 질문

- Q1. PostgreSQL 메이저 버전과 이미지 태그를 무엇으로 할까? 후보는 18.6과 17.11이다. #2의 동작 확인에는 로컬에 있던 `postgres:17`(17.11)을 썼고, `postgres:18.6-bookworm`은 다운로드가 멈췄다. 기다리는 단위: #3-1
- Q2. 로컬 Compose를 Pi용 Compose(#4)와 같은 파일에서 덮어쓰기로 나눌까, 별도 파일로 둘까? `RESEARCH.md` 13장 3번은 Pi용과 맥북용(Prometheus, Grafana) 두 파일만 적고 있다. 기다리는 단위: #3-1
- Q3. 앱 컨테이너의 베이스 이미지(eclipse-temurin 21, 25 JRE)의 정확한 태그는 무엇인가? Docker Hub 페이지에서 아직 확인하지 못했다(`RESEARCH.md` 10.1 (6)). 기다리는 단위: #3-2
- Q4. k6 요청 타임아웃을 몇 초로 하고, `maxVUs`를 얼마로 할까? 앱의 `connectionTimeout` 30초를 그대로 쓰면 VU가 매우 커진다. k6 요청 타임아웃의 기본값은 문서로 확인해야 한다. 기다리는 단위: #3-4
- Q5. E2(닫힌 모델) 스크립트는 어느 이슈에서 만들까? `RESEARCH.md` 13장 4번은 E1과 E2를 함께 적지만 어느 이슈에도 배정되어 있지 않다. #3으로 정하면 단위를 더한다. 기다리는 단위: 없음 (#3-3 전에 정하면 된다)

## 결정 기록

없음

## 작업 단위

### #3-1 로컬 PostgreSQL 기동
- 상태: 대기
- 동작: 기록된 Compose 명령으로 기동하면 PostgreSQL이 `item` 1만 행을 가진 채 뜨고, 맥북에서 jar로 실행한 앱의 `/query?type=pk&id=1`이 200을 반환한다.
- 확인 방법: `SELECT count(*) FROM item`이 10000이다. 세 프로파일 각각에서 `/query`가 200, 없는 id가 404이다. 종료 명령 후 컨테이너가 남지 않는다.
- 필요한 결정: Q1, Q2
- 할 일:
  - [ ] PostgreSQL 이미지 태그를 고정한 Compose 파일을 만들고 `init.sql`을 마운트한다
  - [ ] 기동과 종료 명령을 `CLAUDE.md` 6장과 `README.md`에 기록한다

### #3-2 앱 컨테이너
- 상태: 대기
- 동작: 기록된 Compose 명령에 처리 모델과 풀 크기를 넣어 기동하면 앱 컨테이너가 PostgreSQL에 붙고, `/actuator/info`가 넣은 값을 그대로 보여 준다.
- 확인 방법: 세 프로파일에서 `/actuator/info`의 `processingModel`, `poolSize`, `javaVersion`이 넣은 값과 같고 `/query`가 200이다. 프로파일이나 `POOL_SIZE`를 빠뜨리면 기동이 실패한다.
- 필요한 결정: Q3
- 할 일:
  - [ ] 베이스 이미지 태그를 고정한 앱 이미지 빌드 방법을 만든다
  - [ ] Compose에 앱 서비스를 더하고, 실험 변수를 환경변수로 넘긴다
  - [ ] 명령을 `CLAUDE.md` 6장과 `README.md`에 기록한다

### #3-3 k6 E1 최소 스크립트
- 상태: 대기
- 동작: 도착률, 지속 시간, 쿼리 유형, `maxVUs`, 요청 타임아웃을 인자로 주고 k6 E1 스크립트를 실행하면, 고정 도착률로 `/query`를 호출하고 지연 분포, `dropped_iterations`, `/ping` 기준선이 결과 파일에 남는다.
- 확인 방법: 로컬 앱을 상대로 낮은 도착률(예: 50건/초)로 실행해 `dropped_iterations`가 0이고, 결과 파일에 위 항목이 모두 있다. 인자를 빠뜨리면 실행이 실패한다. 같은 시드로 두 번 실행하면 요청한 id 순서가 같다.
- 필요한 결정: 없음 (`maxVUs`와 요청 타임아웃은 기본값 없이 인자로 받고, 값은 #3-4에서 정한다)
- 할 일:
  - [ ] id를 시드 범위(1~10000)에서 고정 시드로 고르는 E1 스크립트를 만든다
  - [ ] `/ping` 기준선을 함께 잰다
  - [ ] k6 버전과 실행 명령을 `CLAUDE.md` 6장과 `README.md`에 기록한다
  - [ ] 로컬 숫자는 `results/`에 넣지 않는다

### #3-4 `maxVUs`와 요청 타임아웃 결정
- 상태: 대기
- 동작: 정한 `maxVUs`와 요청 타임아웃으로 #3-3 스크립트를 실행하면, 맥북 k6가 그 VU 수를 띄울 수 있고 생성기 CPU가 기록된다.
- 확인 방법: 결정한 값과 근거, 로컬 확인 결과(VU 수, 생성기 CPU, `dropped_iterations`)가 `RESEARCH.md`에 기록되어 있다.
- 필요한 결정: Q4
- 할 일:
  - [ ] k6 요청 타임아웃의 기본값을 문서로 확인한다
  - [ ] 후보 값으로 맥북 k6의 VU 수용 능력을 확인한다
  - [ ] 결정을 `RESEARCH.md` 6.5와 10.1절에 기록한다

## 결과

(완료 시 작성)

## 핸드오프

(아직 없음)
