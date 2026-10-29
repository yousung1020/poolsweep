# RESEARCH.md: 요청 처리 모델과 HikariCP 풀 크기

> 이 파일은 연구의 단일 기준 문서입니다. 새 세션(Claude Code 포함)은 이 파일을 가장 먼저 읽고 시작합니다.
> 상태: 기획 단계 (파일럿 전). 최종 수정: 2026-10-23 (v0.5).
> 논문화 여부는 파일럿 이후 결정합니다. 어느 쪽이든 코드, 데이터, 실험 노트는 공개 저장소로 남깁니다.
> "확인 필요"로 표시된 항목은 제가 아는 범위에서 정리한 것이므로 공식 문서나 원문으로 확인한 뒤 이 문서에 기록합니다.

## 1. 한 줄 요약

Spring Boot(Kotlin)에서 요청 처리 모델(플랫폼 스레드풀 / 가상 스레드 / 코루틴+JDBC)에 따라 HikariCP의 최적 풀 크기와 병목 위치가 어떻게 달라지는지 통제 실험으로 측정하고, 널리 쓰이는 풀 크기 공식의 예측력을 검증한다.

## 2. 배경과 동기

- 플랫폼 스레드 모델에서는 톰캣 스레드 수가 DB 동시성의 암묵적 상한 역할을 한다.
- 가상 스레드는 이 상한을 없애서 병목이 커넥션 풀로 이동한다는 주장이 실무 글에 많다. 하지만 풀 크기를 체계적으로 바꿔가며 측정한 자료는 드물다.
- 코루틴 + 블로킹 JDBC는 실무에서 흔하지만, 블로킹 호출을 보내는 `Dispatchers.IO`의 병렬도 상한(기본 64, 10.1절에서 확인)이 풀 크기와 맞물려 별도 병목이 될 수 있다.
- 풀 크기 공식(`코어 수 × 2 + 유효 스핀들 수`, 리틀의 법칙)은 널리 인용되지만, 처리 모델별로 실측 검증한 자료는 찾지 못했다.
- 개인 연구 프로젝트이며 졸업 이후까지 이어갈 수 있는 넉넉한 일정으로 진행한다. 논문 작성은 선택지이고, 어느 경우든 측정 환경과 데이터가 남는 것이 최소 목표다.

## 3. 핵심 개념 (실험 결과를 해석하는 기준)

| 개념 | 한 줄 설명 | 이 연구에서의 의미 |
|---|---|---|
| 스레드풀 (thread-per-request) | 요청 하나를 스레드 하나가 전담한다. 스레드 수에 상한이 있다 (톰캣 기본 200) | 동시 처리 수의 상한이자, 뒤쪽 DB를 암묵적으로 보호하는 장치 |
| 블로킹 I/O | DB 응답을 기다리는 동안 스레드가 CPU는 안 쓰면서 자리와 메모리를 점유한다 | 필요 스레드 수 ≈ 코어 수 × (1 + 대기 시간 ÷ 연산 시간) |
| 커넥션 풀 | DB 연결을 미리 만들어 빌려 쓰고 반납한다. 풀 크기가 동시 쿼리 수의 상한이다 | 이 연구의 핵심 변수. 비어 있으면 요청은 풀 앞에서 대기한다 (HikariCP 기본 풀 10, 타임아웃 30초: 10.1절에서 확인) |
| 가상 스레드 | 작업과 진행 상태를 담은 가벼운 객체. 캐리어 스레드(코어 수 정도)에 올라타 실행되고, JVM이 블로킹 지점에서 자동으로 내려놓는다 | 스레드 한계를 없애므로 대기가 풀 앞으로 이동한다. `synchronized` 등으로 내려놓지 못하는 핀닝이 변수 (JDK 버전별 차이는 확인 필요) |
| 코루틴 | `suspend` 지점에서만 내려놓을 수 있다. JDBC는 suspend 함수가 아니라서 `withContext(Dispatchers.IO)`로 블로킹 전용 스레드풀에 보낸다 | 그 IO 스레드가 DB 대기 동안 묶인다. 코루틴 + JDBC는 묶임을 없애는 것이 아니라 다른 스레드풀(IO)로 옮기는 것 |
| 리틀의 법칙 | L = λ × W (동시 수 = 처리량 × 체류 시간) | 한계 처리량 = 동시 수 ÷ 점유 시간. 전체 처리량은 CPU, 스레드, 풀 한계 중 가장 작은 값에서 막힌다 (예: 풀 10, 쿼리 18ms → 약 555건/초) |
| 처리량, 지연, 백분위수 | 처리량은 초당 완료 건수, 지연은 대기 + 처리 시간. p99는 느린 1%의 경계 | 평균은 꼬리를 가린다. 풀이 작으면 p99가 먼저, 풀이 크면 DB 경합으로 p50이 먼저 나빠질 것으로 예상 |
| 열린 / 닫힌 부하 모델 | 열린: 도착률을 고정. 닫힌: 가상 사용자 N명이 응답 후 다음 요청 | 닫힌 모델은 과부하를 스스로 가린다 (coordinated omission). 6.4절 참고 |

## 4. 연구 질문과 가설

**RQ1.** 처리 모델에 따라 지속 가능 처리량이 최대가 되는 HikariCP 풀 크기가 달라지는가?
**RQ2.** 그 차이는 쿼리 지연(블로킹 비율), 부하 수준(도착률), JDK 버전에 따라 어떻게 변하는가?
**RQ3.** 코어 수 기반 공식과 리틀의 법칙은 실측 최적 풀 크기를 얼마나 잘 예측하는가?

가설 (검증 대상이며 결과를 보고 수정 가능):
- **H1.** 플랫폼 스레드풀은 스레드 수가 동시성을 제한하므로 최적 풀 크기가 작은 범위에 있고, 가상 스레드는 풀 대기 큐가 길어지며 병목이 풀로 이동한다.
- **H2.** 공식은 "DB 측 최적 동시 쿼리 수"는 대략 맞추지만, 앱 인스턴스별 풀과 대기 시간은 설명하지 못한다.
- **H3.** 코루틴+JDBC에서는 `Dispatchers.IO` 병렬도 상한(기본 64, `limitedParallelism(n)`을 쓰면 n)이 풀 크기와 맞물려 별도 병목이 된다. 풀 크기가 그 상한을 넘는 구간에서는 처리량이 더 늘지 않는다.
- **H4.** JDK 21과 이후 버전(JEP 491 적용 이후)에서 핀닝 양상이 달라 결과가 갈릴 수 있다. (버전별 지원 상태는 확인 필요)

참고 예측값: 이 연구의 환경에서 DB에 코어 2개를 할당하면 공식의 예측은 약 4(유효 스핀들 0 가정)다. 풀 크기 스윕에 4를 반드시 포함해 이 예측을 검증한다. (코어 분리 후 확정)

## 5. 연구 범위

**포함**
- 단일 앱 인스턴스 + 단일 PostgreSQL
- 처리 모델 3종, HikariCP 풀 크기, 쿼리 유형, JDK 버전
- 읽기 중심 워크로드 (PK 조회와 인위적 DB 지연)

**후속 과제 (1차 범위 밖)**
- R2DBC / WebFlux 스택 (r2dbc-pool은 풀 종류가 달라 별도 연구)
- 다중 앱 인스턴스, 분산 환경
- 쓰기 중심, 혼합 읽기·쓰기 워크로드 (microSD 영향 때문에 1차에서는 제외 후보)
- 클라우드 또는 x86 등 다른 하드웨어에서의 재현 (선택)

**구현하지 않는 것 (실험 변수 오염과 일정 소모를 막기 위함)**
- Spring Security / 인증: 요청당 CPU 오버헤드가 늘어 앱의 CPU 한계가 먼저 와서 풀 효과를 가릴 수 있다
- CI/CD 파이프라인, Swagger, JPA/Hibernate 등 ORM, 캐시, 불필요한 부가 기능
- 앱을 인터넷에 노출하지 않는다. LAN 안에서만 접근한다 (포트포워딩 금지)

## 6. 실험 설계 (초안)

### 6.1 시스템 구성 (앱)

- **Kotlin Spring Boot 단일 프로젝트**에서 프로파일(`platform` / `virtual` / `coroutine`)로 처리 모델을 전환한다. 언어가 변수로 끼어들지 않고 서비스·레포지토리 로직을 공유한다. 모델마다 달라지는 것은 컨트롤러 쪽뿐이다.
- **서버 스택 통일**: 세 모델 모두 같은 서버(Tomcat + Spring MVC) 위에서 비교하는 것을 목표로 한다. 서버가 다르면 모델 효과와 서버 차이가 섞인다. MVC에서의 `suspend` 컨트롤러 지원 범위는 버전별로 공식 문서로 확인이 필요하며, 불가능하면 대안과 통제 방법을 정한다.
- **DB 접근**: `JdbcTemplate`(JDBC 직접). JPA/Hibernate는 쓰지 않는다.
- **버전 고정** (D4, D6): Spring Boot 4.1.1 라인(Spring Framework 7.0.9, Tomcat 11)을 쓰고, HikariCP는 Boot 관리 버전(7.0.2)을 별도 지정 없이 쓴다. 가상 스레드 수정이 들어간 HikariCP 7.1.0은 후속 비교 후보로 둔다.
- **풀**: HikariCP. 풀 크기는 환경변수로 주입한다.
- **플랫폼 스레드 프로파일**: 기본 톰캣 스레드풀 (스레드 수는 기록한다).
- **가상 스레드 프로파일**: Spring Boot 설정으로 켠다 (`spring.threads.virtual.enabled=true`, 10.1절에서 확인).
- **코루틴 프로파일**: 블로킹 JDBC를 `withContext`로 실행한다. 병렬도는 `Dispatchers.IO.limitedParallelism(n)`의 n으로 통제하고, "기본값" 수준은 `Dispatchers.IO` 그대로(64) 둔다 (D2).
  - MVC에서 suspend 컨트롤러는 비동기 요청 처리를 거친다. 따라서 이 프로파일은 요청마다 디스패치가 한 번 더 일어나고 톰캣 스레드가 DB 대기 동안 반납된다. 이 차이는 코루틴 모델의 본질로 보고 처리 모델 차이로 인정하며, 결과 해석에서 명시한다 (D3).
- **엔드포인트**: `/query?type=pk|sleep10|sleep50&id=N`(쿼리 유형과 id를 요청 파라미터로 받음), `/ping`(DB를 호출하지 않는 기준 엔드포인트, 세 프로파일 공통의 일반 핸들러)
  - 쿼리 유형 (2026-10-23 결정): 세 유형 모두 같은 PK 조회이고 지연만 다르다. `pk`는 `SELECT id, payload FROM item WHERE id = ?`, `sleep10`과 `sleep50`은 같은 조회에 `pg_sleep(0.010)`, `pg_sleep(0.050)`을 FROM 절로 더한다. id가 있으면 `pg_sleep`은 요청당 정확히 한 번 실행되고 없으면 실행되지 않는다(EXPLAIN ANALYZE로 확인).
  - id는 k6가 시드 범위(1~10000)에서 골라 파라미터로 보낸다. 앱은 받은 값으로만 동작해 세 프로파일에서 같고, k6 시드를 고정하면 요청 순서까지 재현할 수 있다.
- **계측**: Actuator + Micrometer(Prometheus)로 풀 지표를 노출한다.
- **DB**: PostgreSQL 18.6. 이미지는 `postgres:18.6-bookworm@sha256:5c4783a8cc3f0074912164f94dfa1be4e043438f0f707cf968d7af9a0d31a2c8`로 태그와 digest를 함께 고정한다(2026-10-28 연구자 결정). 같은 태그도 기반 이미지 갱신으로 다시 빌드되기 때문이다(이 태그는 2026-10-28에 다시 빌드되었다). PostgreSQL 설정은 이미지 기본값을 그대로 쓴다. `item(id bigint PK, payload text)`에 100바이트 payload 1만 행(약 1.6MB)을 둔다. 스키마와 시드는 DB 컨테이너의 초기화 스크립트(`infra/db/init.sql`)가 만들고, 앱은 기동 중에 스키마를 만들지 않는다.

### 6.2 실험 환경 (하드웨어)

확정 구성은 서버 1대 + 부하 생성기 1대이며 추가 구매는 하지 않는다.

| 역할 | 기기 | 비고 |
|---|---|---|
| 서버 | Raspberry Pi 4B 4GB (앱 + PostgreSQL) | 방열(히트싱크/팬), 정품 전원, 유선 LAN |
| 부하 생성기 + 모니터링 | MacBook Air M1 16GB | k6, Prometheus, Grafana. 실험 중에는 다른 작업을 하지 않는다 |

- **코어 분리**: 서버 안에서 앱은 코어 0-1, PostgreSQL은 코어 2-3에 고정한다 (Docker Compose `cpuset` 또는 `taskset`: 방법은 환경 구축 시 확인). 앱과 DB의 CPU 경쟁을 막기 위함이다.
- Pi에는 앱과 PostgreSQL만 올리고, Prometheus와 Grafana는 맥북에서 Pi를 원격 스크랩한다.
- 메모리(4GB)는 JVM 힙, PostgreSQL, OS 여유로 나눈다. 값은 파일럿에서 확정한다.
- 데이터셋은 작게 유지해 DB 메모리에 모두 올린다. 디스크 I/O 영향이 빠지며, 풀 공식의 "유효 스핀들 수 = 0" 조건에 대응한다.
- 맥북은 Wi-Fi라서 네트워크 지연 편차가 생긴다. `/ping` 기준선을 함께 측정해 네트워크와 프레임워크 기본 지연을 분리해 보고한다.
- 스로틀링(`vcgencmd get_throttled`)과 온도를 실행마다 기록하고, 스로틀링이 발생한 실행은 무효 후보로 표시해 재실행한다.
- 개발과 디버깅은 맥북 로컬에서 해도 되지만, **측정은 항상 이 고정 환경에서만** 한다.
- 한계: 4코어 ARM, 앱 2코어, Wi-Fi 부하 생성기. 논문화 시 명시하고, 절대 수치보다 곡선의 모양과 처리 모델 간 상대 차이를 주장한다.

### 6.3 변수

**설정 변수**

| 변수 | 수준 (초안) |
|---|---|
| 처리 모델 | 플랫폼 스레드풀 / 가상 스레드 / 코루틴+JDBC(Dispatchers.IO) |
| HikariCP 풀 크기 | 2, 4, 8, 16, 32, 64, 128 |
| 쿼리 유형 | 짧은 PK 조회 / 인위적 지연(예: `pg_sleep` 10ms, 50ms) |
| JDK | 21, 25 (D5: JEP 491이 들어간 24 이후 버전. 25는 LTS로 확인, 10.1절) |
| IO 병렬도 (코루틴만) | 기본값(`Dispatchers.IO`, 64) / `Dispatchers.IO.limitedParallelism(n)` 몇 가지 (n 값은 파일럿에서 확정) |

**부하 변수**
- E1: 도착률(건/초)을 단계적으로 올리는 스윕 (범위와 단계는 파일럿에서 확정)
- E2: 동시 사용자 수 N = 50, 200, 1000 (초안)

조합이 많으므로 파일럿에서 범위를 줄이고, 본 실험은 단계적으로 늘린다.

### 6.4 부하 모델

| | 열린 모델 (주 실험, E1) | 닫힌 모델 (보조 실험, E2) |
|---|---|---|
| 규칙 | 도착률 λ(건/초)를 서버 상태와 무관하게 고정 | 가상 사용자 N명이 응답을 받은 뒤에 다음 요청을 보냄 |
| k6 | `ramping-arrival-rate` / `constant-arrival-rate` | VU 기반 시나리오 |
| 용도 | 포화점, 지연 곡선, 풀 크기별 지속 가능 처리량 | 동시성이 정확히 N일 때의 최대 처리량과 지연 분포 |

- 닫힌 모델은 서버가 느려지면 요청이 함께 줄어서 과부하와 대기 시간이 측정에서 빠진다 (coordinated omission). 열린 모델은 도착 시각 기준이라 풀 대기가 지연에 그대로 반영된다.
- 가상 스레드의 효과와 위험은 동시 요청이 폭증할 때 나타나는데, 닫힌 모델은 동시 요청 수를 VU 수로 제한해서 이를 재현하지 못한다.
- 지연은 반드시 클라이언트(k6)에서 측정한다 (톰캣 큐와 풀 대기 시간 포함).
- 이론 참고값: 풀 10 / 쿼리 18ms면 처리 상한은 약 555건/초. 도착률 범위는 이런 이론값(풀 크기 ÷ 쿼리 시간, 코어 수 ÷ CPU 시간)을 참고해 파일럿에서 정한다.

### 6.5 측정 지표

- 완료 처리량(req/s), 지연 p50/p95/p99(클라이언트 측정), 에러율, 타임아웃 수
- 풀 지표 (Micrometer): active, idle, pending, acquire 시간
- DB 지표: 활성 연결 수, CPU
- 가상 스레드 핀닝: JFR의 `VirtualThreadPinned` 이벤트 (JDK별 지원 확인)
- 부하 생성기 건강성: k6의 `dropped_iterations`와 생성기 CPU (D1). k6 문서에 따르면 드롭은 VU 부족(설정) 외에 서버 응답이 느려져 VU가 모두 묶일 때도 생긴다. 그래서 `maxVUs`를 도착률 × 요청 타임아웃 이상으로 잡아 서버 포화로 인한 VU 부족을 줄이고, 드롭이 남으면 원인을 단정하지 않고 생성기 CPU와 함께 기록해 구분한다. 드롭이 0이 아닌 단계는 무효 후보로 표시하며, 지속 가능 처리량 조건 (b)는 6.6 그대로 적용한다.
- 환경 지표: Pi 온도와 스로틀링 여부, `/ping` 기준선 지연

### 6.6 핵심 정의 (사전 고정)

- **지속 가능 처리량**: E1에서 아래를 모두 만족하는 가장 높은 도착률 단계의 완료 처리량.
  (a) 에러율 ≤ 1%, (b) `dropped_iterations` = 0, (c) 단계 후반부에 풀 대기(pending)가 증가 추세가 아님 (큐 안정), (d) p99가 사전에 정한 상한 이하.
  임계값(에러율, p99 상한, 단계 길이)은 파일럿에서 확정하고, 본 실험 전에 이 문서에 기록해 고정한다.
- **최적 풀 크기**: (처리 모델, 쿼리 유형) 조건별로, 지속 가능 처리량이 그 조건의 최대 지속 가능 처리량의 95% 이상인 가장 작은 풀 크기.
- 결과를 보고 정의를 바꾸지 않는다.
- E2는 최적 풀 크기를 정의하지 않고, 고정 동시성에서의 최대 처리량과 지연 분포를 보조 자료로 보고한다.

### 6.7 절차와 통계

- 워밍업 후 단계별 측정 (E1은 도착률 단계마다 측정 구간을 두며 길이는 파일럿에서 확정), 설정당 5회 이상 반복
- 평균과 95% 신뢰구간 보고, 실행 순서 무작위화, 같은 시간대와 같은 온도 조건에서 반복, 반복 사이에 쿨다운
- 백분위수(p99 등)는 실행별 값을 평균하기보다 원자료를 합쳐서 계산하거나 실행별 분포로 보고
- 실험 중에는 다른 작업을 하지 않는다

### 6.8 재현성

- JDK, Spring Boot, JDBC 드라이버, Docker 이미지 태그를 모두 고정해 기록한다 (`latest` 금지).
- 스크립트 하나로 "환경 기동 → 실험 → CSV 저장"이 돌아가게 한다.
- 결과 CSV에 실행 ID, 설정 해시, 스로틀링 여부를 남긴다.
- README에 "이 환경에서 이렇게 돌리면 같은 결과가 나온다"를 정리한다.

### 6.9 도구

k6(부하, 주 실험은 arrival-rate executor), Prometheus + Grafana(수집), Python(pandas, matplotlib)으로 분석. 부하 생성기는 앱과 DB로부터 CPU를 분리한다.

## 7. 확정된 결정

- 주제: 처리 모델 × HikariCP 풀 크기. 코루틴은 **JDBC + HikariCP + Dispatchers.IO**로 한정하고 R2DBC는 후속 과제로 둔다.
- 빌드 도구는 Gradle Kotlin DSL과 Gradle wrapper로 한다(2026-10-23). wrapper가 Gradle 버전을 저장소에 고정하므로 맥북과 Pi에 Gradle을 따로 설치하지 않아도 된다. Maven은 쓰지 않는다.
- 앱은 Kotlin Spring Boot 단일 프로젝트이며 프로파일로 3모델을 전환한다. DB 접근은 JdbcTemplate, ORM은 쓰지 않는다.
- 가상 스레드는 Spring Boot의 가상 스레드 설정으로 켠다 (메커니즘 차이를 최소화).
- 주 실험(E1)은 열린 모델(k6 arrival-rate), 닫힌 모델은 보조(E2)로만 쓴다.
- 지연은 클라이언트에서 측정하고, `dropped_iterations`가 0이 아닌 단계는 무효 후보로 표시한다. 원인(생성기 한계 또는 서버 포화)은 단정하지 않고 생성기 CPU와 함께 기록해 구분한다 (D1).
- 환경은 Pi 4B 1대(앱 코어 0-1 + DB 코어 2-3 분리) + 맥북(k6, 모니터링)으로 고정한다. 추가 구매는 하지 않는다.
- Security, CI/CD 등은 구현하지 않는다 (5장).
- AI 학습은 취미로 병행하며 이 연구의 범위에 포함하지 않는다.
- 논문화 여부는 파일럿 후(1차), 본 실험 후(2차) 두 지점에서 판단한다.
- 졸업작품(Spring Boot + RDB)과는 저장소를 분리한다. 졸작이 있으면 "실제 서비스 워크로드 검증" 장으로 연결할지 나중에 검토한다.

## 8. 선행연구 현황

검색은 웹 검색 기준이며 구글 스칼라를 직접 확인한 것이 아니다. "확인 필요" 항목은 전문을 읽어야 한다.

**학술/준학술**
- Radovan (2024), *Assessing the Efficiency of Java Virtual Threads in Database-Driven Server Applications*. 가상 스레드 vs 전통 스레드, DB 기반 서버. **풀 크기를 변수로 다뤘는지 확인 필요.** (가장 가까운 후보)
- *Optimizing Utilization of Resource Pools in Web Application Servers*, Concurrency and Computation: Practice and Experience 22 (2010). 웹 서버 스레드와 DB 커넥션의 최적 풀 크기를 프로파일링으로 계산하는 방법론. 가상 스레드 이전 연구이며 이론적 배경.
- Pollub JCSI, *Performance analysis of coroutines and other concurrency techniques in Kotlin for I/O operations*. 코루틴, 스레드, 스레드풀, 가상 스레드 비교. DB 풀 관점은 확인 필요.
- Pollub JCSI, *Comparative Analysis of Reactive Programming and Java Virtual Threads* (Spring Boot, PostgreSQL).
- 알버타대 학위논문 (Loomizer): 가상 스레드 처리량 개선이 서버 종류, IO/CPU 비중, 블로킹 지연에 좌우됨.
- NCI 석사 프로젝트 (Pandita, 2024): I/O 작업에서 가상 스레드 우세, CPU 작업은 비슷.
- MIPRO 2022: 가상 스레드와 코루틴의 구조적 동시성 비교.

**실무 자료 (가설의 출처)**
- MariaDB 블로그의 JDBC + 가상 스레드 벤치마크: 풀 크기를 바꿔 본 간이 실험에서 플랫폼 스레드는 작은 풀(4)이, 가상 스레드는 더 많은 연결이 유리하다는 취지. **H1의 예비 근거이자 가장 가까운 실험.**
- InfoQ *Virtual Threads After JDK 24*: 풀 크기 상한을 바꿔 병목 이동을 보여주는 시나리오.
- devgenius *Virtual Threads With Java 24*, lucaberton JDBC 글: 풀 고갈, 핀닝 사례.
- TNG java-virtual-thread-benchmark: 풀 크기 10 고정. chrisgleissner loom-webflux-benchmarks: k6 + bare metal 방법론 참고.
- HikariCP wiki *About Pool Sizing*, PostgreSQL wiki *Number of Database Connections*: 공식의 출처. SSD에서의 분석이 없다고 스스로 밝힌 부분이 있음.
- Kotlin 포럼: `Dispatchers.IO` 기본 상한 64 (H3의 출처). 공식 문서와 로컬 실행으로 확인했다(10.1절).

## 9. 차별점 (조심스러운 주장)

찾은 범위에서는 아래 조합을 함께 다룬 연구를 발견하지 못했다. (없다는 증명이 아니다)
1. 세 처리 모델을 같은 실험 틀에서 풀 크기 스윕으로 비교
2. 코루틴+JDBC의 `Dispatchers.IO` 상한과 풀 크기의 상호작용
3. 풀 크기 공식을 처리 모델별로 실측 검증
4. JDK 버전 변수 포함, 열린 부하 모델 기반 측정, 전체 설정과 원자료 공개

MariaDB 블로그 실험이 부분 선행이므로, "병목이 이동한다" 자체가 아니라 **"얼마나, 어떤 조건에서, 공식이 맞는가"를 정량화**하는 것이 기여다.

## 10. 리스크 / 열린 질문 / TODO

**선행연구**
- [ ] 구글 스칼라 직접 확인: `virtual threads` + `connection pool`, `Loom` + `JDBC`, `Dispatchers.IO` + `HikariCP`, `pool sizing` + `thread-per-request`
- [ ] Radovan (2024) 전문 확인: 풀 크기 변수 여부
- [ ] Pollub 코루틴 논문 전문 확인

**기술 확인 (공식 문서)**
- [x] JDK, Spring Boot, JDBC 드라이버 버전별 가상 스레드 지원 상태 (10.1절)
- [x] Spring MVC에서 `suspend` 컨트롤러 지원 범위. 세 모델을 같은 서버 스택(Tomcat + MVC)에서 돌릴 수 있는지 (10.1절)
- [x] `Dispatchers.IO` 기본 병렬도 상한과 `limitedParallelism` 동작 (10.1절)
- [x] k6 arrival-rate executor 옵션(`preAllocatedVUs`, `maxVUs`)과 `dropped_iterations` 지표 (10.1절)
- [ ] 코어 분리(`cpuset`/`taskset`) 적용 방식, arm64 이미지 지원 (Compose `cpuset` 형식과 arm64 이미지 제공은 10.1절에서 확인. Pi에서 실제로 코어에 고정되는지는 #4에서 확인)
- [x] JDK 25의 LTS 여부와 지원 기간 (D5, 10.1절)
- [x] `limitedParallelism(n)`이 n > 64에서 실제로 n개까지 병렬 실행되는지 로컬 확인 (D2, 10.1절)
- [x] MVC suspend 컨트롤러의 실행 스레드(Unconfined 시작, IO 스레드에서 재개)를 로컬 확인 (10.1절)
- [x] HikariCP 기본값(풀 10, `connectionTimeout` 30초) 확인 (10.1절)

**실험 설계 확정 (파일럿 후 문서에 기록하고 고정)**
- [ ] 지속 가능 처리량의 임계값: 에러율, p99 상한, 단계 길이
- [ ] 도착률 스윕 범위 (이론 상한 참고)
- [ ] 조합 폭발 방지: 파일럿에서 수준을 줄이는 기준
- [ ] 메모리 배분 (JVM 힙, PostgreSQL, OS)

**환경 점검**
- [ ] Pi 방열과 전원 상태 확인, 스로틀링과 온도 기록 방법
- [ ] 코어 분리 검증: 앱 2코어에서 CPU 한계가 풀 한계보다 먼저 오지 않는지 확인 (먼저 온다면 `pg_sleep` 쿼리로 풀 한계를 낮추는 방안 검토)
- [ ] 부하 생성기가 병목이 아닌지 사전 점검 (생성기 CPU, `dropped_iterations`)
- [ ] 맥북 Wi-Fi 지연 편차 측정 (`/ping` p99)
- [ ] (선택) 두 번째 환경 확보 검토: 학교 서버, 학생용 클라우드 혜택 등 (조건은 현재 시점에 직접 확인)

### 10.1 기술 확인 결과 (2026-10-23)

공식 문서 기준. 문서 버전은 확인 당일의 stable(Spring Boot 4.1.1, Spring Framework 7.0.9)이다. "추론"으로 표시한 것은 문서에 직접 적혀 있지 않으며 로컬 확인 대상이다.

**(1) Spring MVC의 `suspend` 컨트롤러**
- MVC와 WebFlux 모두 `@Controller`의 suspend 함수, `Deferred`, `Flow` 반환을 지원한다. `kotlinx-coroutines-core`와 `kotlinx-coroutines-reactor`가 클래스패스에 있어야 한다. → 세 모델을 Tomcat + MVC 한 스택에서 돌릴 수 있다.
- 실행 방식: `InvocableHandlerMethod`가 suspend 함수를 `CoroutinesUtils.invokeSuspendingFunction`으로 호출하고, 이 함수는 **Unconfined 디스패처**로 실행해 `Mono`로 바꾼다. MVC는 단일 값 `Mono`를 `DeferredResult`처럼 처리한다(`request.startAsync()`, 서블릿 컨테이너 스레드 해제, 완료 시 `ASYNC` 재디스패치).
- 따라서 코루틴 프로파일에서는 톰캣 스레드가 DB 대기 동안 묶이지 않고, 톰캣 스레드 수(기본 200)가 동시성 상한이 아니다. 요청당 디스패치가 한 번 더 일어난다.
- 로컬 확인(2026-10-23, 맥북 M1, JDK 21 Temurin 21+35, Boot 4.1.1, 스크래치 프로젝트이며 저장소에는 넣지 않았다): 코루틴은 톰캣 스레드(`http-nio-*-exec-*`)에서 시작하고, `withContext(Dispatchers.IO)` 안과 그 뒤 재개 모두 IO 스레드(`DefaultDispatcher-worker-*`)에서 실행되었다. `request.isAsyncStarted`는 `true`였고, 인터셉터가 `REQUEST` 디스패치와 `ASYNC` 디스패치를 서로 다른 톰캣 스레드에서 한 번씩 기록했다. 일반 블로킹 핸들러는 `REQUEST` 한 번만 기록되었다.
- 같은 앱을 `spring.threads.virtual.enabled=true`로 띄우면 톰캣 쪽은 가상 스레드(`tomcat-handler-*`)가 되지만 `Dispatchers.IO` 작업은 여전히 플랫폼 스레드에서 실행되었다. 따라서 코루틴 프로파일은 가상 스레드 설정을 끈 상태로 실행해야 두 모델이 섞이지 않는다.
- 출처: [Spring Framework Coroutines](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html), [MVC Asynchronous Requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html), [CoroutinesUtils Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/CoroutinesUtils.html), [InvocableHandlerMethod 소스](https://github.com/spring-projects/spring-framework/blob/main/spring-web/src/main/java/org/springframework/web/method/support/InvocableHandlerMethod.java)

**(2) 가상 스레드 설정과 버전별 지원**
- Spring Boot: `spring.threads.virtual.enabled=true`. Java 21 이상이 필요하고 Java 24 이상을 강력히 권장한다. 켜면 스레드풀 설정 프로퍼티가 효과가 없다(예: 톰캣 스레드 수). 가상 스레드는 데몬이므로 `spring.main.keep-alive=true`를 권장한다. ([Boot 문서](https://docs.spring.io/spring-boot/reference/features/spring-application.html))
- Spring Boot 4.1.1: Java 17 ~ 26 호환, Spring Framework 7.0.9 이상, Tomcat 11.0.x. ([System Requirements](https://docs.spring.io/spring-boot/system-requirements.html))
- JDK: JEP 491(`synchronized` 안에서 블로킹해도 캐리어를 놓아줌)이 **JDK 24**에 들어갔다. 남는 핀닝: 네이티브 콜백, 클래스 로딩·초기화 중 블로킹. `jdk.VirtualThreadPinned` JFR 이벤트는 유지·강화되고 `jdk.tracePinnedThreads`는 제거되었다. ([JEP 491](https://openjdk.org/jeps/491)) → 핀닝은 JFR 이벤트로만 측정한다.
- PostgreSQL JDBC: 42.6.0에서 `synchronized`를 `ReentrantLock`으로 교체했다. ([42.6.0 changelog](https://jdbc.postgresql.org/changelogs/2023-03-17-42.6.0-release/))
- Boot 4.1.1 관리 버전: HikariCP 7.0.2, postgresql 42.7.13, kotlinx-coroutines 1.10.2, Kotlin 2.3.21, Tomcat 11.0.24, Micrometer Prometheus 1.17.1. ([Dependency Versions](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html))
- HikariCP 7.1.0 CHANGES: "avoid virtual-thread yield spin in ConcurrentBag" (#2402). Boot 관리 버전(7.0.2)에는 들어 있지 않다. ([CHANGES](https://github.com/brettwooldridge/HikariCP/blob/dev/CHANGES))

**(3) `Dispatchers.IO`와 `limitedParallelism`**
- 병렬로 IO 작업을 하는 스레드 수는 시스템 프로퍼티 `kotlinx.coroutines.io.parallelism`로 제한되며 기본값은 **64와 코어 수 중 큰 값**이다. Pi 4코어에서는 64.
- `Dispatchers.IO.limitedParallelism(n)`으로 얻은 뷰는 **IO의 64 상한에 묶이지 않는다**(탄력성). 스레드는 IO와 공유한다. 즉 `limitedParallelism(128)`이면 128까지 병렬 실행이 가능하다.
- IO와 그 뷰는 `Dispatchers.Default`와 스레드를 공유한다. 상한은 병렬 실행 작업 수의 상한이지 스레드 수의 엄격한 상한이 아니다.
- `limitedParallelism` 문서도 같은 내용이다. `Dispatchers.IO`는 limited parallelism에서 "elastic"이어서 뷰들의 합이 `Dispatchers.IO`의 용량에 묶이지 않고, `newFixedThreadPoolContext(n)`을 `Dispatchers.IO.limitedParallelism(n)`으로 바꿔도 스레드 수 면에서 안전하다고 적혀 있다. 일반 디스패처에서는 모든 뷰의 실효 병렬도가 원본 디스패처의 실제 병렬도를 넘지 못한다는 점과 대비된다.
- 뒤쪽 스케줄러의 스레드 수 상한(`kotlinx.coroutines.scheduler.max.pool.size`)의 기본값은 `MAX_SUPPORTED_POOL_SIZE` = 2^21 − 2(약 209만)이다(소스 master 기준, 1.10.2 태그로 고정해 확인하지 않았다). 풀 크기 스윕의 최대 128은 이 상한과 무관하다.
- 주의: `limitedParallelism`은 코드를 동시에 실행하는 스레드 수를 제한할 뿐, 동시에 존재하는 코루틴 수를 제한하지 않는다(문서의 Pitfall). 이 연구는 블로킹 JDBC를 IO 스레드에서 실행하므로 n이 곧 동시 DB 호출 수의 상한이다.
- 로컬 확인(2026-10-23, 맥북 M1, JDK 21, kotlinx-coroutines 1.10.2, 스크래치 코드): 300ms 동안 블로킹하는 작업 300개를 보내고 동시에 실행 중인 작업 수의 최댓값을 쟀다. `Dispatchers.IO`는 64, `limitedParallelism(16)`은 16, `(64)`는 64, `(128)`은 128, `(200)`은 200이었다. 경과 시간도 ⌈300/n⌉ × 300ms와 맞았다(예: n=128에서 913ms). `-XX:ActiveProcessorCount=4`로 Pi와 같은 코어 수를 흉내 내도 같은 값이 나와 기본값이 max(64, 코어 수)임과 맞았다.
- 출처: [Dispatchers.IO API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-i-o.html), [limitedParallelism API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/limited-parallelism.html), [Tasks.kt](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/src/scheduling/Tasks.kt), [CoroutineScheduler.kt](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/jvm/src/scheduling/CoroutineScheduler.kt)

**(4) k6 arrival-rate executor와 `dropped_iterations`**
- `constant-arrival-rate`: `rate`(필수), `timeUnit`(기본 `1s`), `duration`(필수), `preAllocatedVUs`(필수), `maxVUs`(미설정 시 `preAllocatedVUs`와 같음). `ramping-arrival-rate`: `stages`(필수), `preAllocatedVUs`(필수), `startRate`(기본 0), `timeUnit`, `maxVUs`.
- 남는 VU가 있어야만 반복을 시작한다. `dropped_iterations`(Counter)는 "VU가 부족해서 시작하지 못한 반복 수"다.
- k6 문서는 드롭의 원인을 **설정(VU 부족)**과 **SUT 성능 저하(응답이 길어져 VU가 모두 묶임)** 두 가지로 나눈다. 테스트 중반 이후의 드롭은 SUT 쪽일 가능성이 있다고 적는다.
- 출처: [constant-arrival-rate](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/), [ramping-arrival-rate](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/ramping-arrival-rate/), [Dropped iterations](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/dropped-iterations/), [Metrics reference](https://grafana.com/docs/k6/latest/using-k6/metrics/reference/)

**(5) HikariCP 기본값**
- `maximumPoolSize` 10, `minimumIdle`은 `maximumPoolSize`와 같음(설정하지 않으면 고정 크기 풀), `connectionTimeout` 30000ms(최소 250ms), `idleTimeout` 600000ms(`minimumIdle` < `maximumPoolSize`일 때만 적용), `maxLifetime` 1800000ms, `keepaliveTime` 120000ms. README는 `minimumIdle`을 설정하지 않고 고정 크기 풀로 쓰기를 권장한다.
- 이 연구에 주는 의미: 풀 크기 외의 값은 기본값으로 고정하고 기록한다(CLAUDE.md 2장). 기본값 그대로면 풀이 고정 크기여서 실행 중에 연결 수가 바뀌지 않는다. D1의 `maxVUs` 기준(도착률 × 요청 타임아웃)에 `connectionTimeout` 30초를 넣으면 VU 수가 커진다(예: 500건/초 × 30초 = 15,000 VU). 맥북 k6가 감당하는지, 요청 타임아웃을 무엇으로 볼지는 #3에서 정한다.
- 출처: [HikariCP README](https://github.com/brettwooldridge/HikariCP/blob/dev/README.md)

**(6) 코어 분리와 arm64 이미지 (문서 확인까지)**
- Compose의 `cpuset`은 실행을 허용할 CPU를 범위(`0-3`) 또는 목록(`0,1`)으로 지정한다. `cpus`(소수 CPU 수)와 `mem_limit`도 있고, 설정하면 Deploy Specification의 값과 일치해야 한다. ([Compose services](https://docs.docker.com/reference/compose-file/services/))
- 공식 `postgres` 이미지와 `eclipse-temurin` 이미지는 모두 `arm64v8`을 제공한다. postgres는 확인 시점에 18.6과 17.11이 최신 패치였다(예: `18.6-bookworm`). eclipse-temurin의 21, 25 JRE 정확한 태그는 Docker Hub 페이지에서 확인하지 못했다. ([postgres](https://hub.docker.com/_/postgres), [eclipse-temurin](https://hub.docker.com/_/eclipse-temurin))
- 확인하지 못한 것: Pi OS에서 cgroup cpuset이 켜져 있어 `cpuset`이 실제로 적용되는지. 이는 #4에서 정한다. PostgreSQL 버전은 2026-10-28에 18.6으로 정했다(6.1).

**문서 가정과 다른 점과 결정 (2026-10-23 연구자 결정, 반영 위치는 괄호 안)**
- D1. `dropped_iterations` > 0은 서버 포화로도 생긴다(k6 문서). **결정**: `maxVUs`를 도착률 × 요청 타임아웃 이상으로 잡고, 드롭이 남으면 원인을 단정하지 않고 생성기 CPU와 함께 기록해 구분한다. 6.6의 정의는 바꾸지 않았다. (6.5, 7장)
- D2. `limitedParallelism(n)` 뷰는 `Dispatchers.IO`의 64 상한에 묶이지 않는다. **결정**: IO 병렬도 변수는 `Dispatchers.IO.limitedParallelism(n)`의 n, "기본값" 수준은 `Dispatchers.IO`(64)로 둔다. 문서, 소스, 로컬 실행으로 n > 64에서 n개까지 병렬 실행됨을 확인했고(10.1 (3)), 2장과 H3의 상한 표현을 "기본 64, `limitedParallelism(n)`이면 n"으로 고쳤다. (6.1, 6.3)
- D3. 코루틴 프로파일은 MVC 비동기 처리를 거쳐 톰캣 스레드가 동시성 상한이 아니다. **결정**: 코루틴 모델의 본질로 보고 처리 모델 차이로 인정하며 해석에서 명시한다. (6.1)
- D4. **결정**: Spring Boot 4.1.1 라인(Spring Framework 7.0.9, Tomcat 11)으로 고정한다. (6.1)
- D5. **결정**: 두 번째 JDK는 25다. 확인: JDK 25는 2025-09-16에 GA했고 OpenJDK 프로젝트 페이지는 "most vendors"의 LTS라고 적는다. Temurin 25 (LTS)는 최소 2031-09까지 지원이며, 같은 표에서 Java 21 (LTS)은 최소 2029-12까지다. 최신 패치는 확인 시점(2026-10)에 25.0.4.1이므로 실험 빌드에서는 패치 버전까지 고정해 기록한다. 출처: [OpenJDK JDK 25](https://openjdk.org/projects/jdk/25/), [Adoptium Support](https://adoptium.net/support/). Oracle 지원 로드맵 페이지는 403으로 읽지 못했다. (6.3)
- D6. **결정**: HikariCP는 Boot 관리 버전 7.0.2를 쓴다. 7.1.0은 후속 비교 후보다. (6.1)

**행정과 논문화**
- [ ] 학교의 AI 활용 표기 규정, 졸업논문 요건 확인
- [ ] 논문화 시 투고처 후보 (국내 학회, arXiv)

## 11. 일정과 결정 지점

- 1~2개월: 선행연구 확정(10장 TODO), 환경 구축, 파일럿
- **결정 1**: 파일럿에서 풀 크기에 따른 의미 있는 패턴이 보이는가
- 3~4개월: 본 실험, 이상 결과 재검증
- **결정 2**: 결과를 "왜"까지 설명할 수 있는가 (가능하면 논문 작성)
- 5~6개월: 분석, 작성, 외부 피드백
- 이후: arXiv / 국내 학회

## 12. 저장소 구조 (제안)

```
.
├── RESEARCH.md        # 이 문서
├── LAB_NOTEBOOK.md    # 날짜별 실험 기록 (설정, 결과, 이상한 점)
├── README.md          # 재현 가이드 (환경, 버전, 실행 방법)
├── app/               # Kotlin Spring Boot 앱 (프로파일: platform / virtual / coroutine)
├── infra/             # Compose 파일 (Pi용, 맥북용), DB 초기화
├── load/              # k6 시나리오 (E1: 열린 모델, E2: 닫힌 모델)
├── results/           # 원자료 CSV (실행 ID, 설정 해시 포함)
└── analysis/          # 분석 스크립트와 그래프
```

졸업작품 코드는 이 저장소에 섞지 않는다.

## 13. Claude Code에게 첫 작업 지시

1. 코드를 만들기 전에 이 문서의 5장(범위), 6장(설계), 10장(TODO)을 읽고, 모호한 부분은 질문한다.
2. `app/`: Kotlin Spring Boot 프로젝트. `/query`(쿼리 유형 선택)와 `/ping`을 3개 프로파일로 구현하고, 서비스와 레포지토리는 공유한다. 풀 크기, 처리 모델, IO 병렬도는 환경변수로 주입하고, 쿼리 유형은 요청 파라미터로 받는다(6.1). Actuator + Micrometer 지표를 노출한다. 5장의 "구현하지 않는 것"은 추가하지 않는다.
3. `infra/`: Compose 파일을 둘로 분리한다. (a) Pi용: 앱 + PostgreSQL, `cpuset`으로 코어 분리, arm64 이미지, 메모리 제한 명시. (b) 맥북용: Prometheus + Grafana (+ k6). Prometheus는 Pi의 앱 메트릭을 원격으로 스크랩한다.
4. `load/`: k6 스크립트 두 종류. E1(주): arrival-rate executor로 도착률 스윕, E2(보조): VU 수 변수화. 결과를 CSV로 저장하고 `dropped_iterations`와 `/ping` 기준선도 함께 기록한다.
5. 파일럿 실행 스크립트(설정당 반복, 순서 무작위화, 스로틀링과 온도 기록)와 `LAB_NOTEBOOK.md` 자동 기록, `README.md` 재현 가이드.
6. 모든 버전(JDK, Spring Boot, 드라이버, 이미지 태그)을 고정해 기록한다.

## 14. 변경 이력

- v0.1 (2026-10-22): 초안. 주제, RQ, 가설, 선행연구, 설계 초안
- v0.2 (2026-10-22): 부하 모델을 열린 모델 기준으로 변경, 실험 환경을 Pi 1대 + 맥북으로 확정
- v0.3 (2026-10-22): 종합본으로 재구성. 핵심 개념, 연구 범위, 시스템 구성(Kotlin 단일 프로젝트), 재현성 절을 추가하고 TODO를 분류
- v0.3.1 (2026-10-23): 기술 확인 4건의 결과와 출처를 10.1절에 추가. 설계와 정의는 바꾸지 않았고, 문서와 다른 점은 D1~D6으로 기록해 결정을 기다린다
- v0.4 (2026-10-23): D1~D6 결정을 반영. 6.1(Boot 4.1.1, HikariCP 7.0.2, 코루틴 프로파일 정의와 D3 명시), 6.3(JDK 21/25, IO 병렬도 변수), 6.5와 7장(`dropped_iterations` 해석)을 고쳤다. 6.6의 정의와 임계값은 바꾸지 않았다
- v0.4.1 (2026-10-23): JDK 25의 LTS 여부(D5)와 `limitedParallelism`의 상한 동작(D2)을 웹 검색과 소스로 확인해 10.1절에 기록. 설계는 바꾸지 않았다
- v0.4.2 (2026-10-23): 로컬 확인 2건(MVC suspend의 실행 스레드, `limitedParallelism(n)`의 n > 64 동작) 결과를 10.1절에 기록하고 2장과 H3의 상한 표현을 고쳤다. 코루틴 프로파일은 가상 스레드 설정을 끄고 실행해야 한다는 점을 기록했다. 빌드 도구(Gradle Kotlin DSL)를 7장에 추가했다. HikariCP 기본값과 코어 분리/arm64 이미지의 문서 확인 결과를 (5), (6)에 추가했다
- v0.5 (2026-10-23): #2 앱 뼈대의 결정을 6.1에 기록. 쿼리 유형은 요청 파라미터로 받고(13장 문구를 6.1에 맞춤), 지연 쿼리는 PK 조회에 `pg_sleep`을 더한 형태, id는 k6가 전달, 스키마와 시드는 `infra/db/init.sql`의 1만 행으로 정했다
- v0.6 (2026-10-28): PostgreSQL 버전과 이미지 고정 방식(18.6, 태그와 digest)을 6.1에 기록
