# 앱 코드 해설 (#2)

[app/](../../app/)은 세 가지 요청 처리 방식(`platform`, `virtual`, `coroutine`)을 같은 서비스와 쿼리 위에서 비교하려는 Kotlin Spring Boot 앱입니다. 설계의 기준 문서는 [RESEARCH.md](../../RESEARCH.md) 6.1이며, 이 문서는 코드를 읽는 순서에 맞춰 풀어 설명합니다.

## 1. 큰 그림

- 앱은 하나이고, 실행할 때 프로파일을 정확히 하나만 켭니다. 프로파일은 요청을 처리하는 방식(컨트롤러와 스레드 종류)만 바꿉니다.
- 서비스, 레포지토리, 쿼리, HikariCP 설정은 세 프로파일이 똑같이 씁니다. 그래야 성능 차이가 "처리 방식" 때문이라고 말할 수 있습니다(`AGENTS.md` 2장의 공정성 규칙).
- 요청 한 건은 이렇게 흐릅니다.
  1. k6가 `/query?type=pk&id=123`을 보냅니다.
  2. Tomcat의 요청 스레드가 컨트롤러를 호출합니다.
  3. 컨트롤러가 [ItemRepository](../../app/src/main/kotlin/poolsweep/ItemRepository.kt#L10)를 부릅니다.
  4. 레포지토리가 HikariCP에서 DB 커넥션을 빌립니다. 풀이 비어 있으면 여기서 기다립니다.
  5. PostgreSQL이 조회하고 커넥션이 풀로 돌아갑니다.
- 이 프로젝트가 재려는 것은 4단계의 "풀 대기"입니다. 풀 크기와 처리 방식에 따라 이 대기가 어떻게 달라지는지가 연구 질문입니다.

## 2. 세 프로파일이 다른 곳은 컨트롤러뿐입니다

### platform과 virtual

[BlockingQueryController](../../app/src/main/kotlin/poolsweep/BlockingQueryController.kt#L11)는 `@Profile("platform", "virtual")`로 두 프로파일이 같은 핸들러를 씁니다(11~13행).

- `/query`는 일반 블로킹 메서드입니다. 요청을 받은 스레드가 DB 대기 동안 그대로 붙잡혀 있습니다.
- 두 프로파일의 차이는 요청 스레드의 종류뿐입니다. 이 차이는 코드가 아니라 설정이 만듭니다. [application-platform.yaml](../../app/src/main/resources/application-platform.yaml#L4)는 `spring.threads.virtual.enabled: false`, [application-virtual.yaml](../../app/src/main/resources/application-virtual.yaml#L4)는 `true`입니다.
- **가상 스레드**는 JVM이 관리하는 가벼운 스레드입니다. 블로킹 호출에서 기다릴 때 운영체제 스레드를 놓아 주므로, 같은 스레드 수로 더 많은 요청을 동시에 들고 있을 수 있습니다.

### coroutine

[CoroutineQueryController](../../app/src/main/kotlin/poolsweep/CoroutineQueryController.kt#L24)의 `/query`는 `suspend` 함수입니다.

- 26행의 `withContext(ioDispatcher)`가 핵심입니다. 요청을 받은 Tomcat 스레드는 이 지점에서 풀려나고, DB 호출은 IO 디스패처의 스레드가 맡아 대기 동안 붙잡힙니다.
- 즉 코루틴 프로파일에서는 Tomcat 스레드 수가 동시성의 상한이 아닙니다. 이 차이는 코루틴 모델의 본질로 보고 그대로 둔다는 것이 설계 결정(D3)이며, 15~16행의 주석에 이유가 적혀 있습니다.
- JDBC는 블로킹 API라서 코루틴으로 감싸도 DB 대기 중에는 스레드 하나가 묶입니다. 코루틴이 이득을 보는 지점은 "요청 스레드를 일찍 돌려주는 것"이지 "DB 호출이 비동기가 되는 것"이 아닙니다.
- [IoDispatcherConfig](../../app/src/main/kotlin/poolsweep/CoroutineQueryController.kt#L31)(31~39행)는 IO 병렬도를 실험 변수로 받습니다. 값이 `default`면 `Dispatchers.IO`(기본 상한 64), 숫자 n이면 `Dispatchers.IO.limitedParallelism(n)`입니다. 후자는 64에 묶이지 않고 n이 동시 JDBC 호출 수의 상한이 됩니다(34~35행의 주석, 로컬 확인).
- 프로파일 파일 [application-coroutine.yaml](../../app/src/main/resources/application-coroutine.yaml#L5)은 가상 스레드를 **끕니다**. 켜면 톰캣 쪽만 가상 스레드가 되고 IO 작업은 플랫폼 스레드에 남아 두 모델이 섞이기 때문입니다(4행 주석).

## 3. 공통으로 쓰는 부품

- [ItemRepository](../../app/src/main/kotlin/poolsweep/ItemRepository.kt#L9)는 세 프로파일이 공유하는 유일한 DB 접근 경로입니다. `JdbcTemplate`으로 블로킹 호출을 하며, 어느 스레드에서 부를지는 컨트롤러가 정합니다. 여기를 고치면 세 모델에 똑같이 적용되므로 `AGENTS.md` 8장에 따라 먼저 묻고 고칩니다.
- [QueryType](../../app/src/main/kotlin/poolsweep/QueryType.kt#L9)은 쿼리 유형 세 가지입니다. 모두 같은 PK 조회이고, DB가 커넥션을 붙잡고 있는 시간만 다릅니다.
  - `pk`는 지연이 없고, `sleep10`과 `sleep50`은 `pg_sleep`으로 10ms와 50ms를 더합니다.
  - `pg_sleep`을 `FROM` 절에 둔 이유는 6~8행에 있습니다. 이렇게 하면 id가 있을 때 요청당 정확히 한 번 실행됩니다. 반대로 id가 시드 범위(1~10000) 밖이면 sleep이 실행되지 않으므로 부하 스크립트는 범위 안의 id만 보냅니다.
  - 알 수 없는 유형은 17행에서 HTTP 400을 반환합니다.
- [PingController](../../app/src/main/kotlin/poolsweep/PingController.kt#L8)의 `/ping`은 DB를 거치지 않는 기준선입니다. 네트워크와 Tomcat, MVC의 기본 지연을 따로 재서 `/query` 지연에서 풀과 DB의 몫을 분리해 보려는 것입니다.

## 4. 잘못된 실행을 막는 장치

- [ProcessingModelGuard](../../app/src/main/kotlin/poolsweep/ExperimentSettings.kt#L13)는 처리 모델 프로파일이 정확히 하나가 아니면 기동을 실패시킵니다(15~17행). 프로파일이 없거나 둘이면 컨트롤러가 없거나 겹쳐서, 엉뚱한 설정으로 측정하는 일이 생기기 때문입니다.
- [application.yaml](../../app/src/main/resources/application.yaml#L11)의 `maximum-pool-size: ${POOL_SIZE}`에는 기본값이 없습니다. 값을 빠뜨리면 기동이 실패하고, 실수로 기본 풀 크기로 측정하는 일을 막습니다. 풀 크기 외의 HikariCP 값은 기본값을 그대로 씁니다(9~10행).
- [ExperimentSettingsInfo](../../app/src/main/kotlin/poolsweep/ExperimentSettings.kt#L25)는 `/actuator/info`로 "앱이 실제로 쓰는 값"을 노출합니다(처리 모델, 풀 크기, 가상 스레드 여부, IO 병렬도, JDK, `availableProcessors` 등).
  - 환경변수가 아니라 앱이 읽은 값을 보여 주는 이유는, 오타나 누락이 있으면 기록에 그대로 드러나게 하려는 것입니다(22~23행).
  - 실행 스크립트가 이 값을 결과와 함께 저장합니다.
- [application.yaml](../../app/src/main/resources/application.yaml#L18)의 `management.endpoints.web.exposure.include`는 `health,info,prometheus`입니다. `/actuator/prometheus`에서 `hikaricp_connections_*` 지표가 나오고, 이것을 Prometheus가 수집합니다.

## 5. 테스트가 고정하는 것

[ProcessingModelProfileTest](../../app/src/test/kotlin/poolsweep/ProcessingModelProfileTest.kt#L21)는 DB 없이 확인할 수 있는 실험 타당성 조건을 지킵니다(19~20행).

- 세 프로파일이 각자의 컨트롤러만 등록하고, `/actuator/info`가 처리 모델과 풀 크기, 가상 스레드 여부를 기록한다(44~58행).
- 코루틴 프로파일은 기본값에서 `Dispatchers.IO`를, 병렬도를 주면 제한된 뷰를 쓴다(61~68행).
- 프로파일이 없거나 둘이면 기동이 실패하고(72~75행), `POOL_SIZE`가 없어도 실패한다(78~80행).
- HikariCP는 첫 `getConnection` 전에는 DB에 연결하지 않아서, 실제 조회(`/query`)는 이 테스트가 다루지 않습니다. 그 부분은 로컬 PostgreSQL로 따로 확인했습니다.

## 6. 왜 이렇게 만들었나, 대안은 무엇인가

- **한 앱에 프로파일 세 개**: 앱을 셋으로 나누면 공통 코드가 어긋날 위험이 커집니다. 한 코드에서 프로파일로 갈라야 "바꾼 것은 처리 방식뿐"이라는 주장이 코드로 확인됩니다.
- **추상화를 최소화**: 컨트롤러를 인터페이스로 묶는 식의 일반화는 하지 않았습니다. 실험 장치는 읽고 검증하기 쉬운 코드가 우선이기 때문입니다(`AGENTS.md` 5장).
- **풀 크기를 환경변수로**: 풀 크기는 실험에서 훑는 변수라서 빌드가 아니라 실행 시점에 주입합니다. 실행 스크립트가 값을 기록합니다.
