# #2 앱 뼈대: 세 프로파일, /query, /ping, 지표

상태: 다음 작업

> 2026-10-23에 GitHub 이슈 #2에서 이 파일로 옮겼다. 원본 이슈는 삭제했다.

## 목표

`RESEARCH.md` 6.1과 13장 2번을 구현한다.

## 체크리스트

- [ ] 새 의존성 목록(코루틴 2종, Actuator, Prometheus 레지스트리, JDBC, PostgreSQL 드라이버)을 먼저 보여 주고 승인받는다
- [ ] `app/`에 Spring Boot 4.1.1 + Kotlin + Gradle Kotlin DSL(wrapper) 단일 프로젝트를 만든다
- [ ] 프로파일 `platform` / `virtual` / `coroutine`을 만든다. 코루틴 프로파일은 가상 스레드 설정을 끈다 (`RESEARCH.md` 10.1 (1))
- [ ] 서비스와 레포지토리(JdbcTemplate)는 공유하고, 프로파일별 차이는 컨트롤러와 실행 계층에만 둔다
- [ ] `/query`(쿼리 유형 파라미터)와 `/ping`(DB 미호출)을 만든다
- [ ] 풀 크기, 처리 모델, 쿼리 유형, IO 병렬도는 환경변수로 주입한다
- [ ] Actuator + Micrometer(Prometheus)로 풀 지표를 노출한다
- [ ] 빌드와 실행 명령을 확인해 `CLAUDE.md` 6장에 기록한다

선행 조건이던 #1의 D1~D6 결정은 끝났다.

## 핸드오프

(아직 없음)
