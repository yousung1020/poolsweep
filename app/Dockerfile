# 실행 JDK(21, 25)만 다르고 나머지는 같은 앱 이미지를 만든다(RESEARCH.md 6.3, D5).
# jar는 호스트에서 `./gradlew test bootJar`로 만든 것을 복사한다. 테스트를 거친 jar를 그대로 쓰기 위해서다.
# 두 JRE 모두 Ubuntu 24.04(noble) 기반이라 OS가 JDK 비교의 변수가 되지 않는다.
# 태그는 다시 빌드되므로 다중 아키텍처 digest까지 고정한다(arm64 포함, 2026-10-29 확인).
# FROM에서 쓰는 인자라 첫 FROM보다 앞에 선언한다. 기본값을 두지 않아서 빠뜨리면 `FROM jre`가 되어 빌드가 실패한다.
ARG JDK
FROM eclipse-temurin:21.0.12.1_1-jre-noble@sha256:cd35bb33859641c5374415826a5951845104093eaa401cbe9d3d9023444f1c13 AS jre21
FROM eclipse-temurin:25.0.4.1_1-jre-noble@sha256:ce8543ddd3070ccbeaac2106a74c9387ddeb8d47a1c43348345715c1ef245c7f AS jre25

FROM jre${JDK}
WORKDIR /app
COPY build/libs/poolsweep-app-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# JVM 옵션은 넣지 않고 기본값으로 고정한다(CLAUDE.md 2장). 실제 값은 /actuator/info로 기록한다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
