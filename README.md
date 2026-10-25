# poolsweep

Spring Boot(Kotlin)에서 요청 처리 모델(플랫폼 스레드 / 가상 스레드 / 코루틴+JDBC)에 따라 HikariCP 풀 크기의 최적점이 어떻게 달라지는지 측정하는 연구 저장소입니다. 설계와 결정은 [RESEARCH.md](RESEARCH.md)에 있습니다.

이 문서는 재현 가이드입니다. 아직 앱 뼈대만 있으며, 로컬 인프라(Compose), 부하 스크립트, Pi 배포는 이후 작업에서 추가합니다.

## 고정 버전

- Spring Boot 4.1.1 (Spring Framework 7.0.9, Tomcat 11), Kotlin 2.3.21
- HikariCP, PostgreSQL JDBC 드라이버, kotlinx-coroutines는 Boot가 관리하는 버전을 씁니다(HikariCP 7.0.2, postgresql 42.7.13, kotlinx-coroutines 1.10.2).
- Gradle 9.8.2 (wrapper, 배포본 SHA-256을 `app/gradle/wrapper/gradle-wrapper.properties`에 고정)
- 바이트코드는 Java 21입니다. 실행 JDK는 21과 25를 비교합니다.

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
DB_URL=jdbc:postgresql://localhost:5432/poolsweep DB_USER=postgres DB_PASSWORD=... \
java -jar build/libs/poolsweep-app-0.0.1-SNAPSHOT.jar
```

- `SPRING_PROFILES_ACTIVE`는 `platform`, `virtual`, `coroutine` 중 하나입니다.
- 코루틴 프로파일은 `IO_PARALLELISM`을 받습니다. `default`(생략 시)는 `Dispatchers.IO`이고, 정수 n은 `Dispatchers.IO.limitedParallelism(n)`입니다.
- DB 스키마와 시드 데이터는 [infra/db/init.sql](infra/db/init.sql)이 만듭니다. PostgreSQL 컨테이너의 `/docker-entrypoint-initdb.d/`에 마운트해 씁니다.

## 엔드포인트

- `GET /query?type=pk|sleep10|sleep50&id=N`: id는 1~10000 범위여야 합니다.
- `GET /ping`: DB를 호출하지 않는 기준선입니다.
- `GET /actuator/info`: 실제로 적용된 실험 설정(처리 모델, 풀 크기, IO 병렬도, 가상 스레드 여부, JDK 등)입니다.
- `GET /actuator/prometheus`: HikariCP 풀 지표(`hikaricp_connections_*`) 등입니다.
