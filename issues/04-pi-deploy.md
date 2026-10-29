# #4 Pi 배포와 코어 분리

- 상태: 대기
- 선행: #3 (대기), 연구자의 Pi 방열, 전원, 유선 LAN 점검
- 후행: #5
- 최종 갱신: 2026-10-29
- 출처: 2026-10-23에 GitHub 이슈 #4에서 이 파일로 옮겼다. 원본 이슈는 삭제했다.

## 목표

측정 환경인 Pi 4B에 앱과 PostgreSQL을 올리고, 두 프로세스가 CPU를 서로 빼앗지 않도록 코어를 나눈다. 맥북에서는 Prometheus와 Grafana로 Pi를 원격 수집한다. 측정은 이 고정 환경에서만 하므로, 여기서 정한 구성이 모든 결과의 전제가 된다.

## 완료 기준

- Pi용 Compose로 앱(코어 0-1)과 PostgreSQL(코어 2-3)이 기동하고, 각 컨테이너가 실제로 지정한 코어에서만 도는 것이 확인되어 있다.
- 맥북의 Prometheus가 Pi 앱의 `/actuator/prometheus`를 수집하고 Grafana에서 풀 지표를 볼 수 있다.
- Pi의 온도와 스로틀링을 기록하는 방법이 정해져 있다.
- Pi 배포와 실행 명령이 `CLAUDE.md` 6장과 `README.md`에 기록되어 있다.

## 작업 맥락

### 이미 정해진 것
- 환경은 Pi 4B 4GB(앱 + PostgreSQL) 1대와 MacBook Air M1(k6, Prometheus, Grafana)이며 추가 구매는 하지 않는다. (`RESEARCH.md` 6.2, 7장)
- 앱은 코어 0-1, PostgreSQL은 코어 2-3에 고정한다. (`RESEARCH.md` 6.2)
- Compose의 `cpuset`은 `0-1` 같은 범위나 `0,1` 같은 목록으로 쓴다. `mem_limit`도 있다. (`RESEARCH.md` 10.1 (6))
- PostgreSQL과 eclipse-temurin 이미지는 #3에서 태그와 digest로 고정했고 모두 arm64를 포함한다. 앱 이미지는 호스트에서 만든 jar를 복사해 빌드한다(`app/Dockerfile`). (`RESEARCH.md` 6.1, 10.1 (6))
- 메모리(4GB)는 JVM 힙, PostgreSQL, OS 여유로 나누며 값은 파일럿(#6)에서 확정한다. (`RESEARCH.md` 6.2)
- 스로틀링(`vcgencmd get_throttled`)과 온도를 실행마다 기록하고, 스로틀링이 생긴 실행은 무효 후보로 표시한다. (`RESEARCH.md` 6.2)
- 앱은 LAN 안에서만 접근하고 인터넷에 노출하지 않는다(포트포워딩 금지). (`RESEARCH.md` 5장)

### 지켜야 할 규칙
- Pi에서 명령을 실행하기 전에 연구자에게 알린다. Pi는 측정 환경이라 의도하지 않은 변경이 결과에 영향을 준다. (`CLAUDE.md` 6장)
- 이미지 태그를 고정하고, 배포 방법을 `README.md`에 쓴다. (`CLAUDE.md` 4장)

### 관련 파일과 명령
- [infra/db/init.sql](../infra/db/init.sql): Pi의 PostgreSQL도 같은 시드를 쓴다.
- #3에서 만든 로컬 Compose와 명령(`CLAUDE.md` 6장)을 바탕으로 한다.
- Pi용 Compose와 맥북용 Compose(Prometheus, Grafana)는 `infra/` 아래에 만들 예정이다.

## 열린 질문

- Q1. 앱을 Pi에 어떻게 올릴까? 맥북에서 arm64 이미지를 빌드해 옮기는 방법과 Pi에서 직접 빌드하는 방법이 있다. 기다리는 단위: #4-1
- Q2. 컨테이너 메모리 제한의 초깃값을 얼마로 할까? 최종값은 파일럿(#6)에서 정한다. 기다리는 단위: #4-1
- Q3. Pi OS에서 `cpuset`이 실제로 적용되는지 어떻게 확인할까? `RESEARCH.md` 6.2는 Compose `cpuset`과 `taskset`을 후보로 둔다. 기다리는 단위: #4-2
- Q4. 온도와 스로틀링을 실행 전후에만 기록할까, 실행 중에도 주기적으로 기록할까? 기다리는 단위: #4-4

## 결정 기록

없음

## 작업 단위

### #4-1 Pi에 앱과 PostgreSQL 배포
- 상태: 대기
- 동작: 기록된 배포 명령을 실행하면 Pi에서 앱과 PostgreSQL이 뜨고, 맥북에서 LAN으로 보낸 `/query`가 200을 반환한다.
- 확인 방법: 맥북에서 세 프로파일의 `/query`, `/ping`, `/actuator/info`가 응답하고, `/actuator/info`의 `availableProcessors`와 JDK가 기대값과 같다.
- 필요한 결정: Q1, Q2
- 할 일:
  - [ ] Pi에서 명령을 실행하기 전에 연구자에게 알린다 (`CLAUDE.md` 6장)
  - [ ] arm64 이미지와 메모리 제한을 넣은 Pi용 Compose를 만든다
  - [ ] 배포, 기동, 종료 명령을 `CLAUDE.md` 6장과 `README.md`에 기록한다

### #4-2 코어 분리
- 상태: 대기
- 동작: Pi용 Compose로 기동하면 앱 프로세스는 코어 0-1에서만, PostgreSQL 프로세스는 코어 2-3에서만 실행된다.
- 확인 방법: Q3에서 정한 방법으로 각 프로세스에 허용된 CPU 목록을 확인하고, 부하를 걸었을 때 코어별 사용률이 그 구분과 맞는다.
- 필요한 결정: Q3
- 할 일:
  - [ ] Pi용 Compose에 `cpuset`을 넣는다 (#1에서 넘어옴)
  - [ ] 확인 명령과 결과를 핸드오프에 남긴다

### #4-3 맥북 Prometheus와 Grafana
- 상태: 대기
- 동작: 기록된 맥북용 Compose 명령으로 기동하면 Prometheus가 Pi 앱의 `/actuator/prometheus`를 수집하고, Grafana에서 `hikaricp_connections_*` 지표를 볼 수 있다.
- 확인 방법: Prometheus의 타깃 상태가 정상이고, 부하 중에 `hikaricp_connections_active`가 바뀐다.
- 필요한 결정: 없음
- 할 일:
  - [ ] 이미지 태그를 고정한 맥북용 Compose와 수집 설정을 만든다
  - [ ] 명령을 `CLAUDE.md` 6장과 `README.md`에 기록한다

### #4-4 온도와 스로틀링 기록
- 상태: 대기
- 동작: 기록 명령을 실행하면 Pi의 온도와 `vcgencmd get_throttled` 값이 시각과 함께 파일로 남는다.
- 확인 방법: 출력 파일에 시각, 온도, 스로틀링 값이 있고, 실행 스크립트(#5)가 읽을 수 있는 형식이다.
- 필요한 결정: Q4
- 할 일:
  - [ ] Pi에서 명령을 실행하기 전에 연구자에게 알린다
  - [ ] 기록 방법을 만들고 `CLAUDE.md` 6장과 `README.md`에 기록한다

## 결과

(완료 시 작성)

## 핸드오프

(아직 없음)
