# #3 로컬 Compose + k6 최소 스크립트

- 상태: 완료 (2026-10-30)
- 선행: #2 (완료)
- 후행: #4, #5
- 최종 갱신: 2026-10-30
- 출처: 2026-10-23에 GitHub 이슈 #3에서 이 파일로 옮겼다. 원본 이슈는 삭제했다.

## 목표

맥북에서 PostgreSQL과 앱을 명령 하나로 띄우고, k6 열린 모델(E1) 최소 스크립트로 `/query`에 부하를 걸 수 있게 한다. Pi 배포(#4)와 실행 자동화(#5) 전에 부하 생성 방식과 VU 설정이 결과를 오염시키지 않는지 로컬에서 먼저 확인하려는 것이다.

## 완료 기준

- 로컬 Compose로 PostgreSQL과 앱을 기동하고 종료하는 명령이 `AGENTS.md` 6장과 `README.md`에 기록되어 있다.
- 모든 이미지 태그가 고정되어 있다(`latest` 없음).
- k6 E1 스크립트가 고정 도착률로 `/query`를 호출하고, `dropped_iterations`와 `/ping` 기준선을 결과에 남긴다.
- `maxVUs`와 요청 타임아웃의 값과 근거가 `RESEARCH.md`에 기록되어 있다.
- k6 실행 명령과 k6 버전이 `AGENTS.md` 6장과 `README.md`에 기록되어 있다.

## 작업 맥락

### 이미 정해진 것
- 주 실험(E1)은 열린 모델이며 `constant-arrival-rate` 또는 `ramping-arrival-rate`를 쓴다. 지연은 k6에서 잰다. (`RESEARCH.md` 6.4, 7장)
- `maxVUs`는 도착률 × 요청 타임아웃 이상으로 잡는다. 드롭이 남으면 원인을 단정하지 않고 생성기 CPU와 함께 기록한다. (`RESEARCH.md` 6.5, D1)
- k6의 `maxVUs`는 설정하지 않으면 `preAllocatedVUs`와 같고, `preAllocatedVUs`는 필수이다. (`RESEARCH.md` 10.1 (4))
- HikariCP `connectionTimeout`은 기본값 30초로 고정되어 있다. 이 값으로 D1을 계산하면 500건/초에서 15,000 VU가 된다. (`RESEARCH.md` 10.1 (5))
- 요청은 `/query?type=pk|sleep10|sleep50&id=N`이다. id는 k6가 시드 범위 1~10000에서 고른다. 없는 id면 `pg_sleep`이 실행되지 않아 지연 쿼리가 짧아진다. k6 시드를 고정하면 요청 순서도 재현된다. (`RESEARCH.md` 6.1)
- `/ping`은 DB를 호출하지 않는 공통 핸들러이며, 네트워크와 서버 기본 지연의 기준선이다. (`RESEARCH.md` 6.1, 6.2)
- 스키마와 시드는 PostgreSQL 컨테이너가 `infra/db/init.sql`을 `/docker-entrypoint-initdb.d/`에서 실행해 만든다. 앱은 스키마를 만들지 않는다. (`RESEARCH.md` 6.1)
- 앱은 `SPRING_PROFILES_ACTIVE`, `POOL_SIZE`, `DB_URL`, `DB_USER`, `DB_PASSWORD`(코루틴은 `IO_PARALLELISM`)를 환경변수로 받는다. 처리 모델 프로파일이 하나가 아니거나 `POOL_SIZE`가 없으면 기동이 실패한다. (`AGENTS.md` 6장)
- PostgreSQL 18.6과 Temurin JRE 21.0.12.1, 25.0.4.1 이미지는 태그와 digest로 고정되어 있고 모두 arm64를 포함한다. 앱 이미지는 호스트에서 만든 jar를 복사한다. (`RESEARCH.md` 6.1, 이 이슈의 결정 기록)
- k6는 v2.3.0 공식 바이너리를 SHA-256으로 고정해 `load/install-k6.sh`로 `load/.bin/k6`에 설치한다. (`RESEARCH.md` 6.9, 이 이슈의 결정 기록)
- E1 스크립트 `load/e1.js`는 모든 값을 기본값 없는 인자로 받고, `/ping` 기준선을 부하 전과 부하 중 두 구간에서 잰다. (`RESEARCH.md` 6.5, `AGENTS.md` 6장)
- 실행 JDK는 21과 25를 비교한다. (`RESEARCH.md` 6.3, D5)

### 지켜야 할 규칙
- 로컬에서 얻은 숫자는 `results/`에 넣지 않는다. 동작 확인용 숫자임을 밝힌다. (`AGENTS.md` 3장)
- 인프라 명령은 확인한 뒤 `AGENTS.md` 6장에 기록하고, 기록 전에는 추측해서 실행하지 않는다. (`AGENTS.md` 6장)
- 이미지 태그와 도구 버전을 고정하고, 새 실행 방법은 `README.md`에도 쓴다. (`AGENTS.md` 4장)
- PostgreSQL과 톰캣 설정은 기본값으로 둔다. 바꿔야 할 것 같으면 먼저 묻는다. (`AGENTS.md` 2장)

### 관련 파일과 명령
- [infra/db/init.sql](../infra/db/init.sql): PostgreSQL 컨테이너가 실행할 시드 스크립트이다.
- [app/src/main/resources/application.yaml](../app/src/main/resources/application.yaml): 앱이 받는 환경변수가 정의되어 있다.
- [README.md](../README.md): 앱 빌드와 실행 방법이 있다. 이번에 인프라와 부하 실행 절을 더한다.
- 앱 빌드와 실행 명령은 `AGENTS.md` 6장의 "앱" 항목이다.
- [infra/compose.yaml](../infra/compose.yaml): 맥북과 Pi가 함께 쓰는 기본 Compose 파일이다. `db`와 `app` 서비스가 있다.
- [app/Dockerfile](../app/Dockerfile): 호스트에서 만든 jar를 고정된 JRE 이미지에 복사한다. JDK는 빌드 인자 `JDK`로 고른다.
- 로컬 PostgreSQL과 앱 컨테이너의 명령은 `AGENTS.md` 6장의 "로컬 PostgreSQL", "로컬 앱 컨테이너" 항목이다.
- [load/install-k6.sh](../load/install-k6.sh): k6 바이너리를 버전과 SHA-256으로 고정해 설치한다.
- [load/e1.js](../load/e1.js): E1 최소 스크립트이다. 실행 명령은 `AGENTS.md` 6장의 "k6" 항목이다.

## 열린 질문

- Q4. k6 요청 타임아웃을 몇 초로 하고, `maxVUs`를 얼마로 할까? 앱의 `connectionTimeout` 30초를 그대로 쓰면 VU가 매우 커진다. k6 요청 타임아웃의 기본값은 문서로 확인해야 한다. 기다리는 단위: #3-4

## 결정 기록

- 2026-10-28 Q1. PostgreSQL 버전과 이미지 → `postgres:18.6-bookworm@sha256:5c4783a8cc3f0074912164f94dfa1be4e043438f0f707cf968d7af9a0d31a2c8`(다중 아키텍처 digest, Docker Hub에서 2026-10-28 확인, arm64 포함). 최신 메이저라 지원 기간이 길고, 18에서 바뀐 기본 동작(비동기 I/O, 데이터 체크섬)은 메모리에 다 올라가는 읽기 전용 PK 조회에 영향이 작을 것으로 추론했다. 설정은 기본값을 그대로 쓴다. 태그는 다시 빌드되므로 digest까지 고정한다. 기록 위치: `RESEARCH.md` 6.1 (v0.6)
- 2026-10-29 Q3. 앱 컨테이너의 베이스 이미지 → `eclipse-temurin:21.0.12.1_1-jre-noble@sha256:cd35bb33859641c5374415826a5951845104093eaa401cbe9d3d9023444f1c13`과 `eclipse-temurin:25.0.4.1_1-jre-noble@sha256:ce8543ddd3070ccbeaac2106a74c9387ddeb8d47a1c43348345715c1ef245c7f`(둘 다 arm64 포함, 2026-10-26 빌드, `docker buildx imagetools inspect`로 2026-10-29 확인). 태그에 패치 버전이 드러나 기록에 유리하고, 두 JDK가 같은 OS(Ubuntu 24.04)를 써서 OS가 JDK 비교의 변수가 되지 않는다. 기록 위치: `RESEARCH.md` 6.1 (v0.7)
- 2026-10-29 앱 이미지 빌드 방식 → 호스트에서 `./gradlew test bootJar`로 만든 jar를 Dockerfile이 JRE 이미지에 복사하고, JDK는 빌드 인자 `JDK`로 고른다. 테스트를 거친 jar를 그대로 쓰고 이미 기록된 빌드 명령을 재사용하려는 것이다. #4 Q1(Pi에 앱을 올리는 방법)의 전제가 된다. 기록 위치: `RESEARCH.md` 6.1 (v0.7)
- 2026-10-29 Q5. E2(닫힌 모델) 스크립트 → 결정 1 뒤의 본 실험 준비 이슈에서 만든다. 결정 1은 E1만으로 판단하고, 결정 1에서 설계가 바뀌면 E2도 다시 써야 하기 때문이다. 기록 위치: `RESEARCH.md` 6.3 (v0.8)
- 2026-10-29 Q6. k6 설치와 버전 고정 → GitHub 릴리스의 v2.3.0 macOS arm64 zip을 `load/install-k6.sh`가 내려받아 SHA-256(`b2417a30…862c7d`)으로 확인한 뒤 `load/.bin/k6`에 둔다. brew는 버전을 고정할 수 없고, `grafana/k6` 컨테이너는 Docker VM의 NAT와 CPU, 파일 디스크립터 제한이 지연과 VU 수에 끼어든다. 기록 위치: `RESEARCH.md` 6.9 (v0.8)
- 2026-10-29 `/ping` 기준선을 재는 시점 → `/query` 부하 전 구간과 부하 중 저율 병행을 모두 쓴다. 부하 중 `/ping`은 풀을 거치지 않으므로 앱 CPU나 톰캣 큐 쪽 대기를 구분하는 단서가 된다. 기록 위치: `RESEARCH.md` 6.5 (v0.8)
- 2026-10-28 Q2. Compose 파일 구성 → 기본 파일 `infra/compose.yaml`에 DB(이미지, `init.sql`, 계정)를 두고, #4에서 `infra/compose.pi.yaml`이 `cpuset`과 메모리 제한만 덮어쓴다. 로컬에서 확인한 DB 정의를 Pi가 그대로 써서 두 환경의 DB 설정이 어긋나지 않게 하려는 것이다. 맥북 모니터링(#4-3)은 별도 파일이다. 기록 위치: 이 이슈

## 작업 단위

### #3-1 로컬 PostgreSQL 기동
- 상태: 완료 (2026-10-29, fa421d1)
- 동작: 기록된 Compose 명령으로 기동하면 PostgreSQL이 `item` 1만 행을 가진 채 뜨고, 맥북에서 jar로 실행한 앱의 `/query?type=pk&id=1`이 200을 반환한다.
- 확인 방법: `SELECT count(*) FROM item`이 10000이다. 세 프로파일 각각에서 `/query`가 200, 없는 id가 404이다. 종료 명령 후 컨테이너가 남지 않는다.
- 필요한 결정: Q1, Q2 (2026-10-28 결정됨)
- 할 일:
  - [x] PostgreSQL 이미지 태그를 고정한 Compose 파일을 만들고 `init.sql`을 마운트한다
  - [x] 기동과 종료 명령을 `AGENTS.md` 6장과 `README.md`에 기록한다

### #3-2 앱 컨테이너
- 상태: 완료 (2026-10-29, 8337b93)
- 동작: 기록된 Compose 명령에 처리 모델과 풀 크기를 넣어 기동하면 앱 컨테이너가 PostgreSQL에 붙고, `/actuator/info`가 넣은 값을 그대로 보여 준다.
- 확인 방법: 세 프로파일에서 `/actuator/info`의 `processingModel`, `poolSize`, `javaVersion`이 넣은 값과 같고 `/query`가 200이다. 프로파일이나 `POOL_SIZE`를 빠뜨리면 기동이 실패한다.
- 필요한 결정: Q3 (2026-10-29 결정됨)
- 할 일:
  - [x] 베이스 이미지 태그를 고정한 앱 이미지 빌드 방법을 만든다
  - [x] Compose에 앱 서비스를 더하고, 실험 변수를 환경변수로 넘긴다
  - [x] 명령을 `AGENTS.md` 6장과 `README.md`에 기록한다

### #3-3 k6 E1 최소 스크립트
- 상태: 완료 (2026-10-29)
- 동작: 도착률, 지속 시간, 쿼리 유형, `maxVUs`, 요청 타임아웃을 인자로 주고 k6 E1 스크립트를 실행하면, 고정 도착률로 `/query`를 호출하고 지연 분포, `dropped_iterations`, `/ping` 기준선이 결과 파일에 남는다.
- 확인 방법: 로컬 앱을 상대로 낮은 도착률(예: 50건/초)로 실행해 `dropped_iterations`가 0이고, 결과 파일에 위 항목이 모두 있다. 인자를 빠뜨리면 실행이 실패한다. 같은 시드로 두 번 실행하면 요청한 id 순서가 같다.
- 필요한 결정: Q5, Q6 (2026-10-29 결정됨. `maxVUs`와 요청 타임아웃은 기본값 없이 인자로 받고, 값은 #3-4에서 정한다)
- 할 일:
  - [x] id를 시드 범위(1~10000)에서 고정 시드로 고르는 E1 스크립트를 만든다
  - [x] `/ping` 기준선을 함께 잰다
  - [x] k6 버전과 실행 명령을 `AGENTS.md` 6장과 `README.md`에 기록한다
  - [x] 로컬 숫자는 `results/`에 넣지 않는다

### #3-4 `maxVUs`와 요청 타임아웃 결정
- 상태: 완료 (2026-10-30)
- 동작: 정한 `maxVUs`와 요청 타임아웃으로 #3-3 스크립트를 실행하면, 맥북 k6가 그 VU 수를 띄울 수 있고 생성기 CPU가 기록된다.
- 확인 방법: 결정한 값과 근거, 로컬 확인 결과(VU 수, 생성기 CPU, `dropped_iterations`)가 `RESEARCH.md`에 기록되어 있다.
- 필요한 결정: Q4 (2026-10-30 결정됨)
- 할 일:
  - [x] k6 요청 타임아웃의 기본값을 문서로 확인한다
  - [x] 후보 값으로 맥북 k6의 VU 수용 능력을 확인한다
  - [x] 결정을 `RESEARCH.md` 6.5와 10.1절에 기록한다

## 결과

- **로컬 PostgreSQL 및 앱 컨테이너 인프라 완성**: `infra/compose.yaml`로 PostgreSQL 18.6과 Temurin JRE(21/25) 기반 앱 컨테이너를 환경변수 검증 하에 단일 명령으로 기동·종료할 수 있게 되었다.
- **k6 v2.3.0 공식 바이너리 고정 및 설치**: SHA-256 검증 기반 `load/install-k6.sh`를 구비하여 재현성을 확보했다.
- **k6 열린 모델(E1) 스크립트 작성**: `load/e1.js`로 부하 전/중 `/ping` 기준선 측정, 결정론적 id 해시 수열 생성, JSON 요약 통계를 구현했다.
- **Q4 결정 및 대규모 VU 수용성 검증**:
  - k6 기본 타임아웃은 60초이며, 현실적 웹 SLA 한계와 생성기 부담을 고려해 요청 타임아웃은 10초로 고정했다.
  - `maxVUs`는 `RATE * (10 + 1) * 1.2`로 산출한다.
  - 과부하 시 동적 스케일업 지연으로 인한 드롭을 방지하기 위해 `preAllocatedVUs = maxVUs`로 사전 할당해야 함을 발견했다.
  - 맥북(Apple Silicon)에서 `ulimit -n 65536` 환경 하에 약 4,000 VU(300건/초, sleep50 과부하)를 드롭 0으로 안정적으로 띄울 수 있음을 검증했다.

## 핸드오프

### 핸드오프 (2026-10-28, #3-1)
- 한 일: #3-1이 기다리던 Q1(PostgreSQL 18.6, 태그와 digest 고정)과 Q2(기본 Compose + Pi 덮어쓰기)를 연구자에게 받아 결정 기록과 `RESEARCH.md` 6.1(v0.6)에 남겼다. Docker Hub에서 18.6과 17.11 태그가 모두 arm64를 제공하고 2026-10-28에 다시 빌드된 것을 확인했다. 코드와 Compose 파일은 아직 만들지 않았다.
- 남은 일: #3-1의 할 일 전부(Compose 파일 작성, 시드와 `/query` 확인, 명령 기록)
- 막힌 점 / 확인 필요: `postgres:18.6-bookworm` 이미지를 맥북에 받았고, `RepoDigests`가 위 digest와 같으며 아키텍처가 arm64인 것을 확인했다. 이 채팅은 길어서 연구자 결정으로 #3-1 구현을 새 채팅으로 넘겼다.
- 다음 세션 첫 작업: `infra/compose.yaml` 작성

### 핸드오프 (2026-10-29, #3-1)
- 한 일: [infra/compose.yaml](../infra/compose.yaml)을 만들었다.
  - 역할과 기능: `db` 서비스 하나를 정의한다. 이미지는 태그와 digest로 고정하고, `init.sql`을 읽기 전용으로 마운트하며, 계정은 `poolsweep`/`poolsweep`이고, 5432 포트를 연다. healthcheck(`pg_isready`)로 `up --wait`가 시드 적재 뒤에 돌아오게 한다. PostgreSQL 설정은 넘기지 않으며, 앱 서비스와 Pi용 `cpuset`, 메모리 제한은 아직 없다.
  - 쓰이는 곳: 지금은 맥북에서 jar로 실행한 앱이 접속한다. 앞으로 #3-2의 앱 서비스와 #4의 `compose.pi.yaml`이 이 파일을 쓴다.
  - 없으면 안 되는 이유: 이 파일이 없으면 DB를 손으로 띄워야 해서, 예를 들어 Pi와 맥북에서 이미지 digest나 시드 적재 방식이 달라져도 알아차릴 수 없다.
  - 전/후 비교: 전에는 `infra/`에 `db/init.sql`만 있었다. 후에는 고정 이미지와 시드 마운트를 가진 Compose 파일이 있다.
- 확인 결과 (로컬 맥북, 커밋 `376fbec` 기준 작업 트리): `up -d --wait` 후 `SELECT count(*) FROM item`이 10000이고, 서버 버전은 `PostgreSQL 18.6 ... aarch64`이다. jar로 실행한 앱(풀 5)에서 세 프로파일 모두 `/query?type=pk&id=1`과 `type=sleep10&id=2`가 200, `id=99999`가 404였고, `/actuator/info`의 `processingModel`과 `virtualThreadsEnabled`가 프로파일과 맞았다. `down -v` 후 `poolsweep` 프로젝트의 컨테이너와 볼륨이 남지 않았다.
- 남은 일: #3-2(앱 컨테이너), #3-3, #3-4
- 막힌 점 / 확인 필요: DB 계정 이름과 암호를 `poolsweep`으로 정했다. LAN 전용 실험 계정이라 별도 결정 없이 정했으며, 바꾸려면 Compose, `AGENTS.md` 6장, `README.md`를 함께 고친다. #3-2는 Q3(eclipse-temurin 21, 25 JRE 태그와 digest)의 결정이 먼저 필요하다.
- 다음 세션 첫 작업: Q3를 위해 eclipse-temurin 21, 25 JRE 태그와 다중 아키텍처 digest를 확인해 연구자에게 결정을 받는다.

### 핸드오프 (2026-10-29, #3-2)
- 한 일: Q3와 빌드 방식을 연구자에게 받아 결정 기록과 `RESEARCH.md` 6.1(v0.7)에 남기고, 앱 컨테이너를 만들었다.
  - [app/Dockerfile](../app/Dockerfile)
    - 역할과 기능: JRE 21, 25 이미지를 태그와 digest로 고정해 두고, 빌드 인자 `JDK`로 고른 쪽에 `build/libs`의 jar를 복사한다. JVM 옵션은 넣지 않는다. jar를 만들지는 않으므로 호출하는 쪽이 먼저 `./gradlew test bootJar`를 실행해야 한다. [app/.dockerignore](../app/.dockerignore)가 빌드 컨텍스트를 jar 하나로 줄인다.
    - 쓰이는 곳: 지금은 `infra/compose.yaml`의 `app` 서비스가 빌드한다. 앞으로 #4의 Pi 배포와 #5의 실행 스크립트가 쓴다(미연결).
    - 없으면 안 되는 이유: 이 파일이 없으면 JDK 21과 25를 비교할 때 호스트 JDK에 의존하게 되어, 예를 들어 Pi와 맥북의 JDK 패치 버전이 달라도 결과에 드러나지 않는다.
    - 전/후 비교: 전에는 앱을 호스트 JDK로 jar 실행만 할 수 있었다. 후에는 고정된 JRE 이미지로 띄울 수 있다.
  - [infra/compose.yaml](../infra/compose.yaml)
    - 역할과 기능: `app` 서비스를 더했다. 실험 변수(`SPRING_PROFILES_ACTIVE`, `POOL_SIZE`, `IO_PARALLELISM`)와 빌드 인자 `JDK`는 이름만 적어서, 셸에 있을 때만 넘긴다. 접속 정보는 `db` 서비스로 고정하고, DB가 healthy가 된 뒤에 뜬다.
    - 쓰이는 곳: 지금은 로컬에서 기록된 명령으로 쓴다. #3-3의 k6가 이 앱에 부하를 건다(미연결).
    - 없으면 안 되는 이유: `POOL_SIZE: ${POOL_SIZE}`처럼 값을 쓰면 빠뜨렸을 때 빈 문자열이 넘어간다. 빈 값을 이름만 적는 방식으로 바꾸면 변수가 아예 설정되지 않아 앱 기동이 실패하고, 엉뚱한 풀 크기로 측정되는 일을 막는다. 또 `down`처럼 변수가 필요 없는 명령도 변수 없이 돈다.
    - 전/후 비교: 전에는 `db` 서비스만 있었다. 후에는 `app` 서비스가 더해졌고, DB만 띄우는 명령은 `up -d --wait db`로 바뀌었다.
- 확인 결과 (로컬 맥북, 커밋 `fa421d1` 위의 작업 트리, 동작 확인용): JDK 21과 25 각각에서 세 프로파일 모두 `/query?type=pk&id=1`과 `type=sleep10&id=2`가 200, `id=99999`가 404였다. `/actuator/info`의 `processingModel`, `poolSize`(7), `ioParallelism`(코루틴 16, 나머지 n/a), `javaVersion`(21.0.12.1, 25.0.4.1)이 넣은 값과 같았다. `JDK`를 빠뜨리면 빌드가 `FROM jre`에서 실패했고, `POOL_SIZE`를 빠뜨리면 `NumberFormatException`으로, 프로파일을 빠뜨리면 `IllegalStateException`으로 앱이 종료 코드 1로 끝났다. `up -d --wait db`로 DB만 띄워 1만 행을 확인했고, `down -v` 뒤에 컨테이너와 볼륨이 남지 않았다.
- 남은 일: #3-3, #3-4
- 막힌 점 / 확인 필요:
  - 컨테이너의 `availableProcessors`는 맥북 Docker VM의 8이었다. Pi에서는 `cpuset`(#4-2)에 따라 달라지므로 그때 다시 본다.
  - 컨테이너 메모리 제한이 없어서 JVM 기본 힙이 Docker VM 메모리를 따른다. 값은 #4 Q2와 #6 Q2에서 정한다.
  - zsh에서 명령을 변수에 담아 `$C up`처럼 쓰면 공백이 나뉘지 않아 실패한다. `AGENTS.md` 6장에 적어 두었다.
- 다음 세션 첫 작업: #3-3 전에 Q5(E2 스크립트를 어느 이슈에서 만들지)를 연구자에게 받고, k6 버전을 확인한다.

### 핸드오프 (2026-10-29, #3-3)
- 한 일: Q5(E2는 결정 1 뒤로), Q6(공식 바이너리 + SHA-256), `/ping` 기준선 시점(부하 전 + 부하 중)을 연구자에게 받아 결정 기록과 `RESEARCH.md`(v0.8)에 남기고, k6를 설치해 E1 스크립트를 만들었다.
  - [load/install-k6.sh](../load/install-k6.sh)
    - 역할과 기능: k6 v2.3.0 macOS arm64 zip을 내려받아 고정한 SHA-256과 맞을 때만 `load/.bin/k6`에 설치하고 버전을 출력한다. macOS arm64가 아니면 실패한다. Pi나 다른 OS용 설치는 하지 않는다.
    - 쓰이는 곳: 지금은 연구자가 맥북에서 한 번 실행한다. 앞으로 #5의 실행 스크립트가 `load/.bin/k6`를 호출한다(미연결).
    - 없으면 안 되는 이유: 없으면 brew 등으로 설치하게 되어, 예를 들어 `brew upgrade` 뒤에 k6가 바뀌어도 파일럿과 본 실험의 생성기가 달라진 것을 알 수 없다.
    - 전/후 비교: 전에는 맥북에 k6가 없었다. 후에는 버전과 해시가 고정된 k6가 저장소 밖으로 커밋되지 않는 위치에 있다.
  - [load/e1.js](../load/e1.js)
    - 역할과 기능: `ping_pre`(부하 전 `/ping`), `query`(고정 도착률 `/query`), `ping_load`(부하 중 `/ping`) 세 `constant-arrival-rate` 시나리오를 돈다. 인자 11개는 모두 필수이다. id는 시드와 시나리오 안의 반복 번호로만 정한다. `SUMMARY_OUT`에 설정과 k6 지표 전체를 JSON으로 쓴다. 도착률 스윕, 실행 ID와 커밋 해시, 버전, Pi 온도 기록은 하지 않는다(#5의 일).
    - 쓰이는 곳: 지금은 맥북에서 명령으로 직접 실행한다. 앞으로 #3-4의 VU 확인과 #5의 실행 스크립트가 호출한다(미연결).
    - 없으면 안 되는 이유: 예를 들어 `MAX_VUS`에 기본값이 있으면 실행 스크립트가 값을 빠뜨려도 작은 VU 수로 돌아, 서버 포화가 아닌 VU 부족으로 생긴 드롭이 측정에 섞인다.
    - 전/후 비교: 전에는 `load/`가 없었다. 후에는 설치 스크립트와 E1 스크립트가 있다.
- 확인 결과 (로컬 맥북, 커밋 `8337b93` 위의 작업 트리, platform 프로파일, JDK 21, 풀 4, 동작 확인용이며 `results/`에 넣지 않았다):
  - 인자를 빠뜨리면 `필수 인자 RATE이(가) 없습니다`로 종료 코드 107이었다.
  - 50건/초, `sleep10`, 20초에서 `query` 1001건, 실패 0, 타임아웃 0, 세 시나리오의 `dropped_iterations`가 모두 0이었다. 요약 JSON에 지연 분포, 실패율, 타임아웃 수, 시나리오별 드롭(0일 때도 `count: 0`)이 있었다.
  - `--http-debug`로 같은 시드(42) 두 번을 실행해 요청 줄의 id 501개를 비교했다. 두 실행의 순서가 같았고, Python으로 따로 구현한 계산과도 같았다. 시드 43의 수열과 같은 위치에서 겹친 id는 0개였다.
  - 드롭과 타임아웃이 실제로 집계되는지 일부러 일으켜 보았다. `MAX_VUS=1`, `sleep50`, 50건/초에서 `query` 드롭이 170건이었고, 풀 4, `sleep50`, 200건/초, 타임아웃 1초에서 타임아웃이 1866건이었다.
- 막힌 점 / 확인 필요:
  - `/ping`의 VU를 도착률 × 타임아웃과 딱 같게 잡았더니 과부하 중 `ping_load`에서 드롭이 6건 났다. 타임아웃된 반복이 타임아웃보다 조금 더 걸리기 때문으로 보고 `PING_RATE × (REQ_TIMEOUT_S + 1)`로 고쳤으며, 고친 뒤에는 0이었다. #3-4에서 `MAX_VUS`를 정할 때도 같은 여유가 필요해 보인다.
  - 과부하 실행 바로 뒤의 정상 실행이 크게 느려졌다(`query` p50 약 7.2초, `ping_pre`가 모두 타임아웃). 클라이언트가 끊은 요청을 서버가 계속 처리하는 적체가 다음 실행으로 넘어온 것으로 보이며, 풀 지표가 0으로 돌아온 뒤에는 정상이었다. #5의 쿨다운 설계에서 "풀 pending과 active가 0일 때 다음 실행"을 조건으로 삼을지 정해야 한다.
  - 과부하 중 k6가 실패한 요청마다 경고 한 줄을 stderr로 쓴다. 수천 건/초에서 생성기 CPU에 영향을 주는지 #3-4에서 본다.
  - `name` 태그를 붙여서 원자료 CSV의 `url`에는 id가 남지 않는다. id마다 시계열이 생기는 것을 막으려는 것이며, id 순서는 시드로 다시 계산할 수 있다.
  - k6 버전은 요약 JSON에 들어가지 않는다. 실행마다 `k6 version`을 기록하는 일은 #5의 실행 스크립트가 맡는다.
- 다음 세션 첫 작업: #3-4. k6 요청 타임아웃의 기본값을 문서로 확인하고, Q4(요청 타임아웃과 `maxVUs`)의 선택지와 추천안을 연구자에게 제시한다.

### 핸드오프 (2026-10-30, #3-4)
- 한 일: Q4(요청 타임아웃 10초, `maxVUs = RATE * 11 * 1.2`)를 연구자에게 받아 결정 기록과 `RESEARCH.md` 6.5, 10.1(v0.9)에 남겼다. k6 공식 문서에서 HTTP 요청 타임아웃 기본값이 60s임을 확인했다. 맥북 로컬에서 300건/초, sleep50 극심한 과부하(타임아웃 1986건) 조건으로 검증한 결과, `preAllocatedVUs`를 `maxVUs`와 같게 사전 할당(`3960`)해야 k6 동적 스케일업 지연으로 인한 드롭을 막고 `dropped_iterations=0`을 유지할 수 있음을 확인하여 규칙으로 확정했다. 맥북에서 `ulimit -n 65536` 환경 하에 약 4,000 VU 생성이 안정적으로 동작함을 확인했다. 이슈 #3을 완료했다.
- 남은 일: #4 (Pi 배포와 모니터링)
- 막힌 점 / 확인 필요:
  - macOS 기본 `ulimit -n`이 256이므로, 향후 대규모 부하 실행 시 실행 스크립트(#5)에서 `ulimit -n 65536`으로 늘려 주어야 소켓 부족 오류를 막을 수 있다.
  - 과부하 시 stderr로 출력되는 warning 로그는 k6의 전체 처리량이나 CPU에 유의미한 병목을 주지 않았으나, 대규모 실행 시 조용한 모드나 로그 리다이렉션을 고려한다.
- 다음 세션 첫 작업: #4 (Pi 배포와 모니터링). [issues/04-pi-deploy.md](04-pi-deploy.md)를 읽고 #4-1(Pi 방열, 전원, 네트워크 상태 점검 및 Compose 배포)을 시작한다.
