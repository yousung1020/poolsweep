# poolsweep

Spring Boot(Kotlin)에서 요청 처리 모델(플랫폼 스레드 / 가상 스레드 / 코루틴+JDBC)에 따라 HikariCP 풀 크기의 최적점이 어떻게 달라지는지 측정하는 연구 저장소입니다. 설계와 결정은 [RESEARCH.md](RESEARCH.md)에 있습니다.

이 문서는 재현 가이드입니다. 지금은 앱, 로컬 Compose(PostgreSQL, 앱 컨테이너), k6 E1(열린 모델) 스크립트까지 있으며, Pi 배포와 실행 자동화는 이후 작업에서 추가합니다.

## 고정 버전

- Spring Boot 4.1.1 (Spring Framework 7.0.9, Tomcat 11), Kotlin 2.3.21
- HikariCP, PostgreSQL JDBC 드라이버, kotlinx-coroutines는 Boot가 관리하는 버전을 씁니다(HikariCP 7.0.2, postgresql 42.7.13, kotlinx-coroutines 1.10.2).
- Gradle 9.8.2 (wrapper, 배포본 SHA-256을 `app/gradle/wrapper/gradle-wrapper.properties`에 고정)
- 바이트코드는 Java 21입니다. 실행 JDK는 21과 25를 비교합니다.
- PostgreSQL 18.6: `postgres:18.6-bookworm@sha256:5c4783a8cc3f0074912164f94dfa1be4e043438f0f707cf968d7af9a0d31a2c8`(태그와 다중 아키텍처 digest를 함께 고정). 설정은 이미지 기본값입니다.
- 앱 컨테이너의 JRE: `eclipse-temurin:21.0.12.1_1-jre-noble@sha256:cd35bb33859641c5374415826a5951845104093eaa401cbe9d3d9023444f1c13`, `eclipse-temurin:25.0.4.1_1-jre-noble@sha256:ce8543ddd3070ccbeaac2106a74c9387ddeb8d47a1c43348345715c1ef245c7f`([app/Dockerfile](app/Dockerfile)). JVM 옵션은 넣지 않습니다.
- 확인한 Docker 환경: Docker 29.9.2, Compose v5.6.1 (맥북)
- k6 v2.3.0 (macOS arm64 공식 바이너리, SHA-256을 [load/install-k6.sh](load/install-k6.sh)에 고정)

## 로컬 PostgreSQL

저장소 루트에서 실행합니다. Docker와 Compose v2 이상이 있어야 합니다.

```sh
docker compose -f infra/compose.yaml up -d --wait db   # 기동. 시드 1만 행 적재가 끝나면 돌아옵니다.
docker compose -f infra/compose.yaml down -v           # 종료. 볼륨까지 지워 다음 기동 때 시드를 다시 만듭니다.
```

- 접속 정보는 `jdbc:postgresql://localhost:5432/poolsweep`, 계정과 암호는 `poolsweep`입니다. LAN 안에서만 쓰는 실험용 계정입니다.
- 스키마와 시드는 [infra/db/init.sql](infra/db/init.sql)이 처음 기동할 때 만듭니다. 앱은 스키마를 만들지 않습니다.

## 앱 빌드와 실행

로컬에 JDK 21이 있어야 합니다. Gradle은 wrapper가 내려받습니다.

```sh
cd app
./gradlew test bootJar
```

실행에는 처리 모델 프로파일 하나와 풀 크기, DB 접속 정보가 필요합니다. 하나라도 빠지면 기동이 실패합니다.

```sh
SPRING_PROFILES_ACTIVE=platform \
POOL_SIZE=4 \
DB_URL=jdbc:postgresql://localhost:5432/poolsweep DB_USER=poolsweep DB_PASSWORD=poolsweep \
java -jar build/libs/poolsweep-app-0.0.1-SNAPSHOT.jar
```

- `SPRING_PROFILES_ACTIVE`는 `platform`, `virtual`, `coroutine` 중 하나입니다.
- 코루틴 프로파일은 `IO_PARALLELISM`을 받습니다. `default`(생략 시)는 `Dispatchers.IO`이고, 정수 n은 `Dispatchers.IO.limitedParallelism(n)`입니다.
- DB는 위 "로컬 PostgreSQL"로 띄웁니다.

## 앱 컨테이너

앱을 컨테이너로 띄우면 DB도 함께 뜹니다. 이미지는 호스트에서 만든 jar를 복사하므로 먼저 jar를 만듭니다.

```sh
(cd app && ./gradlew test bootJar)
JDK=21 SPRING_PROFILES_ACTIVE=platform POOL_SIZE=4 \
  docker compose -f infra/compose.yaml up -d --build --wait
docker compose -f infra/compose.yaml down -v
```

- `JDK`는 `21` 또는 `25`입니다. 빠뜨리면 이미지 빌드가 실패합니다.
- `SPRING_PROFILES_ACTIVE`, `POOL_SIZE`, `IO_PARALLELISM`은 위 "앱 빌드와 실행"과 같습니다. 셸에 없는 값은 컨테이너에도 넘기지 않아서, 프로파일이나 `POOL_SIZE`를 빠뜨리면 앱이 기동하지 못합니다.
- 앱이 뜨는 데 몇 초가 걸립니다. `/ping`이 응답한 뒤 `/actuator/info`로 적용된 값을 확인합니다.

## 부하 생성 (k6 E1)

맥북(Apple Silicon)에서 저장소 루트를 기준으로 실행합니다. k6는 brew가 아니라 아래 스크립트로 설치합니다. brew는 버전을 고정할 수 없기 때문입니다.

```sh
bash load/install-k6.sh   # load/.bin/k6에 설치하고 버전을 출력합니다.
```

[load/e1.js](load/e1.js)는 고정 도착률로 `/query`를 호출하고, `/ping` 기준선을 부하 전 구간과 부하 중에 함께 잽니다. 인자는 모두 필수이며 하나라도 빠지면 k6가 종료 코드 107로 실패합니다.

```sh
load/.bin/k6 run -q \
  -e BASE_URL=http://localhost:8080 -e QUERY_TYPE=pk \
  -e RATE=50 -e DURATION_S=20 -e PRE_VUS=20 -e MAX_VUS=500 -e REQ_TIMEOUT_S=10 \
  -e SEED=42 -e PING_RATE=5 -e PING_PRE_S=5 \
  -e SUMMARY_OUT=/tmp/e1-summary.json \
  load/e1.js
```

- `RATE`와 `DURATION_S`는 `/query`의 도착률(건/초)과 부하 구간 길이(초)입니다. `/query` 부하는 `PING_PRE_S`초 뒤에 시작합니다.
- `PRE_VUS`와 `MAX_VUS`는 `/query` 시나리오의 `preAllocatedVUs`와 `maxVUs`입니다. `/ping`의 VU 수는 `PING_RATE × (REQ_TIMEOUT_S + 1)`로 스크립트가 정합니다.
- `REQ_TIMEOUT_S`는 `/query`와 `/ping`에 함께 쓰는 요청 타임아웃(초)입니다.
- `SEED`가 같으면 요청하는 id 순서(1~10000)가 같습니다. k번째 반복의 id는 시드와 k로만 정해집니다.
- `SUMMARY_OUT`에는 받은 설정, 지연 분포(`query_duration`, `ping_pre_duration`, `ping_load_duration`), 실패율, 타임아웃 수, 시나리오별 `dropped_iterations`, k6 기본 지표가 JSON으로 남습니다. 요청별 원자료가 필요하면 `--out csv=<경로>`를 더합니다.
- 과부하로 실행한 뒤에는 서버가 끊긴 요청을 한동안 계속 처리합니다. 다음 실행 전에 `/actuator/prometheus`의 `hikaricp_connections_pending`과 `hikaricp_connections_active`가 0인지 확인합니다.

## 엔드포인트

- `GET /query?type=pk|sleep10|sleep50&id=N`: id는 1~10000 범위여야 합니다.
- `GET /ping`: DB를 호출하지 않는 기준선입니다.
- `GET /actuator/info`: 실제로 적용된 실험 설정(처리 모델, 풀 크기, IO 병렬도, 가상 스레드 여부, JDK 등)입니다.
- `GET /actuator/prometheus`: HikariCP 풀 지표(`hikaricp_connections_*`) 등입니다.
