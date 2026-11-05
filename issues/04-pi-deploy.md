# #4 Pi 배포와 코어 분리

- 상태: 대기
- 선행: #3 (완료), 연구자의 Pi 방열, 전원, 유선 LAN 점검
- 후행: #5
- 최종 갱신: 2026-11-05
- 출처: 2026-10-23에 GitHub 이슈 #4에서 이 파일로 옮겼다. 원본 이슈는 삭제했다.

## 목표

측정 환경인 Pi 4B에 앱과 PostgreSQL을 올리고, 두 프로세스가 CPU를 서로 빼앗지 않도록 코어를 나눈다. 맥북에서는 Prometheus와 Grafana로 Pi를 원격 수집한다. 측정은 이 고정 환경에서만 하므로, 여기서 정한 구성이 모든 결과의 전제가 된다.

## 완료 기준

- Pi용 Compose로 앱(코어 0-1)과 PostgreSQL(코어 2-3)이 기동하고, 각 컨테이너가 실제로 지정한 코어에서만 도는 것이 확인되어 있다.
- 맥북의 Prometheus가 Pi 앱의 `/actuator/prometheus`를 수집하고 Grafana에서 풀 지표를 볼 수 있다.
- Pi의 온도와 스로틀링을 기록하는 방법이 정해져 있다.
- Pi 배포와 실행 명령이 `AGENTS.md` 6장과 `README.md`에 기록되어 있다.

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
- Pi에서 명령을 실행하기 전에 연구자에게 알린다. Pi는 측정 환경이라 의도하지 않은 변경이 결과에 영향을 준다. (`AGENTS.md` 6장)
- 이미지 태그를 고정하고, 배포 방법을 `README.md`에 쓴다. (`AGENTS.md` 4장)

### 관련 파일과 명령
- [infra/db/init.sql](../infra/db/init.sql): Pi의 PostgreSQL도 같은 시드를 쓴다.
- #3에서 만든 로컬 Compose와 명령(`AGENTS.md` 6장)을 바탕으로 한다.
- Pi용 Compose와 맥북용 Compose(Prometheus, Grafana)는 `infra/` 아래에 만들 예정이다.

## 열린 질문

- Q3. Pi OS에서 `cpuset`이 실제로 적용되는지 어떻게 확인할까? `RESEARCH.md` 6.2는 Compose `cpuset`과 `taskset`을 후보로 둔다. 기다리는 단위: #4-2
- Q4. 온도와 스로틀링을 실행 전후에만 기록할까, 실행 중에도 주기적으로 기록할까? 기다리는 단위: #4-4

## 결정 기록

- 2026-11-01 Q1. 앱을 Pi에 어떻게 올릴까? → 맥북에서 arm64로 빌드해 Pi로 옮긴다. 맥북 M1이 이미 arm64이고 빌드가 로컬에서 검증되어 있으며, Pi에 빌드 부하와 도구 설치를 더하지 않아 측정 환경 변경이 가장 적기 때문이다. 기록 위치: `RESEARCH.md` 6.1
- 2026-11-01 Q2. 컨테이너 메모리 제한의 초깃값을 얼마로 할까? → 앱 1.5GB, DB 1.5GB로 한다. 4GB에서 OS 여유 약 1GB를 남기는 대칭 값이며, 최종값은 파일럿(#6)에서 확정한다. 기록 위치: `RESEARCH.md` 6.2
- 2026-11-05 진행 순서. Pi를 쓸 수 없는 동안 무엇을 할까? → Pi 없이 확인할 수 있는 #4-3(맥북 Prometheus와 Grafana)을 먼저 진행하고, Pi 대상 확인은 #4-5로 나눈다. #4-3은 선행 단위가 없고 수집 대상 주소만 바꾸면 되며, 실행 환경이 다른 확인은 단위를 나누는 규칙(`docs/WRITING.md` 2.3)에 따른다. Pi용 Compose(`cpuset`, 메모리 제한)는 Pi에서만 검증되므로 #4-1에서 만든다. 기록 위치: 이 이슈

## 작업 단위

### #4-1 Pi에 앱과 PostgreSQL 배포
- 상태: 대기
- 동작: 기록된 배포 명령을 실행하면 Pi에서 앱과 PostgreSQL이 뜨고, 맥북에서 LAN으로 보낸 `/query`가 200을 반환한다.
- 확인 방법: 맥북에서 세 프로파일의 `/query`, `/ping`, `/actuator/info`가 응답하고, `/actuator/info`의 `availableProcessors`와 JDK가 기대값과 같다.
- 필요한 결정: Q1, Q2 (2026-11-01 결정됨)
- 할 일:
  - [ ] Pi에서 명령을 실행하기 전에 연구자에게 알린다 (`AGENTS.md` 6장)
  - [ ] arm64 이미지와 메모리 제한을 넣은 Pi용 Compose를 만든다
  - [ ] 배포, 기동, 종료 명령을 `AGENTS.md` 6장과 `README.md`에 기록한다

### #4-2 코어 분리
- 상태: 대기
- 동작: Pi용 Compose로 기동하면 앱 프로세스는 코어 0-1에서만, PostgreSQL 프로세스는 코어 2-3에서만 실행된다.
- 확인 방법: Q3에서 정한 방법으로 각 프로세스에 허용된 CPU 목록을 확인하고, 부하를 걸었을 때 코어별 사용률이 그 구분과 맞는다.
- 필요한 결정: Q3
- 할 일:
  - [ ] Pi용 Compose에 `cpuset`을 넣는다 (#1에서 넘어옴)
  - [ ] 확인 명령과 결과를 핸드오프에 남긴다

### #4-3 맥북 Prometheus와 Grafana (→ #4-5로 나눔)
- 상태: 대기
- 동작: 기록된 맥북용 Compose 명령으로 기동하면 Prometheus가 맥북에서 도는 로컬 앱의 `/actuator/prometheus`를 수집하고, Grafana에서 `hikaricp_connections_*` 지표를 볼 수 있다. 수집 대상 주소는 설정으로 바꿀 수 있어야 한다.
- 확인 방법: 맥북에서 로컬 앱을 띄우고 k6로 부하를 걸었을 때, Prometheus의 타깃 상태가 정상이고 `hikaricp_connections_active`가 바뀐다.
- 필요한 결정: 없음. 단, 맥북용 Prometheus와 Grafana 이미지는 새 의존성이므로 시작할 때 연구자에게 먼저 묻는다(`AGENTS.md` 8장).
- 할 일:
  - [ ] 이미지 태그를 고정한 맥북용 Compose와 수집 설정을 만든다
  - [ ] 명령을 `AGENTS.md` 6장과 `README.md`에 기록한다

### #4-5 Pi 앱 수집 확인
- 상태: 대기
- 동작: #4-3의 수집 대상 주소를 Pi 앱으로 바꾸면 Prometheus가 LAN으로 Pi 앱의 `/actuator/prometheus`를 수집하고, Grafana에서 풀 지표를 볼 수 있다.
- 확인 방법: Prometheus의 타깃 상태가 정상이고, 맥북에서 Pi 앱으로 부하를 걸었을 때 `hikaricp_connections_active`가 바뀐다.
- 선행: #4-1, #4-3
- 필요한 결정: 없음
- 할 일:
  - [ ] Pi 앱이 뜬 상태에서 Pi에 명령을 실행해야 하면 먼저 연구자에게 알린다 (`AGENTS.md` 6장)
  - [ ] 수집 대상 주소를 Pi로 바꾸고 확인 결과를 핸드오프에 남긴다

### #4-4 온도와 스로틀링 기록
- 상태: 대기
- 동작: 기록 명령을 실행하면 Pi의 온도와 `vcgencmd get_throttled` 값이 시각과 함께 파일로 남는다.
- 확인 방법: 출력 파일에 시각, 온도, 스로틀링 값이 있고, 실행 스크립트(#5)가 읽을 수 있는 형식이다.
- 필요한 결정: Q4
- 할 일:
  - [ ] Pi에서 명령을 실행하기 전에 연구자에게 알린다
  - [ ] 기록 방법을 만들고 `AGENTS.md` 6장과 `README.md`에 기록한다

## 결과

(완료 시 작성)

## 핸드오프

### 핸드오프 (2026-11-05, #4 착수 전 정리)
- 한 일: 단위 작업은 시작하지 않았고, #4를 시작하기 위한 결정과 문서 정리만 했다. `app/`, `load/`, `infra/`는 바꾸지 않았다.
  - Q1(맥북에서 arm64로 빌드해 Pi로 옮긴다)과 Q2(메모리 제한 초깃값은 앱 1.5GB, DB 1.5GB)를 연구자에게 받아 결정 기록과 [RESEARCH.md](../RESEARCH.md) 6.1, 6.2에 남겼다(v0.10).
  - Pi를 쓸 수 없어서 #4-3을 먼저 진행하기로 했다(결정 기록 2026-11-05). `docs/WRITING.md` 2.3에 따라 #4-3을 로컬 앱 대상으로 좁히고, Pi 대상 확인은 새 단위 #4-5로 나누었다.
  - [STATUS.md](../STATUS.md)를 #4-3 기준으로 고쳐 썼다.
  - 이 갱신(`RESEARCH.md`, `issues/04-pi-deploy.md`, `STATUS.md`)은 커밋하지 않았다. 직전 커밋은 `9199f4f`이다.
- 남은 일: #4-3, #4-1, #4-2, #4-4, #4-5. 진행 순서는 #4-3이 먼저이고, 나머지는 Pi가 있어야 한다.
- 막힌 점 / 확인 필요:
  - Pi가 아직 없다. #4-1, #4-2, #4-4, #4-5는 Pi를 쓸 수 있게 될 때까지 시작할 수 없다.
  - Prometheus와 Grafana 이미지는 새 의존성이므로 #4-3을 시작할 때 후보를 연구자에게 먼저 묻는다(`AGENTS.md` 8장).
  - [infra/compose.yaml](../infra/compose.yaml) 1행 주석은 `compose.pi.yaml`이 `cpuset`과 메모리 제한만 덮어쓴다고 한다. Q1에 따라 Pi에는 미리 빌드한 이미지를 옮기므로, 앱 서비스의 `build:`를 대신할 `image:`도 필요해진다. #4-1에서 Pi용 Compose를 만들 때 함께 정한다. 이미지 전달 방식(파일 전송 또는 레지스트리)도 #4-1에서 정한다.
  - 푸시 여부는 로컬의 `origin/main` 추적 정보로만 보았고 fetch는 하지 않았다.
- 다음 세션 첫 작업: 위 문서 갱신의 커밋을 연구자와 정한 뒤(제안 메시지: `docs: #4-3 선행 진행과 #4-5 분할, Q1·Q2 설계 기록 반영`), #4-3을 시작한다. 시작할 때 이슈 #4의 상태를 `진행 중`으로 바꾸고 Prometheus와 Grafana 이미지 후보를 먼저 묻는다.
