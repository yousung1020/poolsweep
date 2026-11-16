# #4 Pi 배포와 코어 분리

- 상태: 진행 중
- 선행: #3 (완료), 연구자의 Pi 방열, 전원, 유선 LAN 점검
- 후행: #5
- 최종 갱신: 2026-11-16
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
- [infra/compose.monitoring.yaml](../infra/compose.monitoring.yaml): 맥북용 Prometheus와 Grafana이며 #4-3에서 만들었다. - [infra/compose.pi.yaml](../infra/compose.pi.yaml): Pi용 덮어쓰기 파일이며 #4-1에서 만들었다(미커밋, Pi에서는 아직 실행하지 않았다). `cpuset`은 #4-2에서 더한다.

## 열린 질문

- Q3. Pi OS에서 `cpuset`이 실제로 적용되는지 어떻게 확인할까? `RESEARCH.md` 6.2는 Compose `cpuset`과 `taskset`을 후보로 둔다. 기다리는 단위: #4-2
- Q4. 온도와 스로틀링을 실행 전후에만 기록할까, 실행 중에도 주기적으로 기록할까? 기다리는 단위: #4-4
- Q5. Pi의 memory cgroup을 언제 어떻게 켜서 Q2의 메모리 제한(앱 1.5GB, DB 1.5GB)을 적용할까? 알려진 방법은 `/boot/firmware/cmdline.txt`에 `cgroup_enable=memory`를 더하고 재부팅하는 것이며, `cgroup_disable=memory`가 이미 있는 상태에서 이 방법이 통하는지는 확인 필요하다. 기다리는 단위: 아직 없음(파일럿 #6 전에 단위를 만든다)

## 결정 기록

- 2026-11-01 Q1. 앱을 Pi에 어떻게 올릴까? → 맥북에서 arm64로 빌드해 Pi로 옮긴다. 맥북 M1이 이미 arm64이고 빌드가 로컬에서 검증되어 있으며, Pi에 빌드 부하와 도구 설치를 더하지 않아 측정 환경 변경이 가장 적기 때문이다. 기록 위치: `RESEARCH.md` 6.1
- 2026-11-01 Q2. 컨테이너 메모리 제한의 초깃값을 얼마로 할까? → 앱 1.5GB, DB 1.5GB로 한다. 4GB에서 OS 여유 약 1GB를 남기는 대칭 값이며, 최종값은 파일럿(#6)에서 확정한다. 기록 위치: `RESEARCH.md` 6.2
- 2026-11-05 진행 순서. Pi를 쓸 수 없는 동안 무엇을 할까? → Pi 없이 확인할 수 있는 #4-3(맥북 Prometheus와 Grafana)을 먼저 진행하고, Pi 대상 확인은 #4-5로 나눈다. #4-3은 선행 단위가 없고 수집 대상 주소만 바꾸면 되며, 실행 환경이 다른 확인은 단위를 나누는 규칙(`docs/WRITING.md` 2.3)에 따른다. Pi용 Compose(`cpuset`, 메모리 제한)는 Pi에서만 검증되므로 #4-1에서 만든다. 기록 위치: 이 이슈
- 2026-11-05 #4-3 이미지. 맥북용 Prometheus와 Grafana를 어느 버전으로 고정할까? → `prom/prometheus:v3.15.1@sha256:fa3302279710be2f21a8301ec17a8b4f2301fd829bd113839b3b6b02b6f191a2`, `grafana/grafana:13.3.0@sha256:488984b716c0862639f91a698adbe2862677a5a07bad42da5f03f3030883b205`로 한다. 둘 다 정식 릴리스 중 최신이며(Prometheus v3.16.0은 rc.4라 제외) amd64와 arm64를 포함하고, digest는 Docker Hub API가 돌려준 다중 아키텍처 매니페스트 기준이다. Grafana 13.3.0의 릴리스 날짜는 확인하지 못했다. 기록 위치: 이 이슈
- 2026-11-05 스크랩 주기. Prometheus의 `scrape_interval`과 `scrape_timeout`은? → 5초와 3초로 고정한다. 짧은 k6 실행에서도 `hikaricp_connections_active`의 변화를 볼 수 있고, Pi 앱에는 초당 0.2건만 가는 값이다. 실험 변수가 아니라 고정값이므로 실험 중 바꾸지 않는다. 기록 위치: 이 이슈, `infra/compose.monitoring.yaml`
- 2026-11-16 Pi 포트. Pi에서 8080을 쓸 수 없는데 어떻게 할까? → Pi에서만 호스트 포트 9080으로 공개한다. 컨테이너 안의 포트(8080)와 앱 설정은 그대로라서 세 프로파일의 공정성은 유지된다. 로컬 맥북은 8080이다. 8080을 쓸 수 없는 이유는 연구자가 밝히지 않았다. 기록 위치: `RESEARCH.md` 6.2, `infra/compose.pi.yaml`
- 2026-11-16 이미지 전달 방식. 맥북에서 만든 앱 이미지를 Pi로 어떻게 옮길까? → `docker save` 파일을 SSH로 전송해 `docker load`한다. 레지스트리나 계정 같은 새 의존성이 없고 맥북에서 검증한 이미지를 그대로 옮길 수 있다. 기록 위치: `RESEARCH.md` 6.2
- 2026-11-16 이미지 태그. 앱 이미지 태그는? → `poolsweep-app:<커밋 해시>-jdk<21|25>`. 결과를 커밋으로 추적하기 위한 것이며, 제안에 연구자가 이의를 제기하지 않아 채택했다. 기록 위치: `RESEARCH.md` 6.2
- 2026-11-16 `availableProcessors`. #4-1에서 Pi 앱의 값을 어떻게 다룰까? → 합격 기준 없이 나온 값을 기록만 한다. 예상은 4이며 `cpuset` 적용 뒤의 값(2)은 #4-2에서 확인한다. 단위 경계를 지키기 위해서다. 기록 위치: 이 이슈
- 2026-11-16 Pi 스왑. 스왑을 4GiB로 늘릴까? → 바꾸지 않고 2GiB로 유지해 환경 기록에 남긴다. 연구자가 처음 SSD 스왑 4GiB를 말했으나 루트는 microSD였고, 스왑이 지연 측정에 개입할 수 있어 변수 외 고정 규칙(`AGENTS.md` 2장)에 따라 유지를 선택했다. 실행 전후 스왑 사용량 확인은 #4-4에서 정한다. 기록 위치: `RESEARCH.md` 6.2, `README.md`
- 2026-11-16 Pi의 Docker. 어떻게 설치할까? → Docker 공식 apt 저장소에서 docker-ce와 docker-ce-cli 5:29.9.2, containerd.io 2.4.2-4, docker-compose-plugin 5.6.1로 고정하고 `apt-mark hold`를 걸었다. 맥북의 29.9.2와 Compose v5.6.1에 맞춘 것이다. `docker` 그룹은 root 수준 권한을 주므로 `sudo`로 실행한다. `--no-install-recommends`로 설치해 buildx 플러그인은 없다(Pi에서는 이미지를 빌드하지 않는다). 기록 위치: `RESEARCH.md` 6.2, `README.md`
- 2026-11-16 메모리 cgroup. Pi 부트 파라미터에 `cgroup_enable=memory`를 더하고 재부팅할까? → 연구자가 처음에 승인했다가 곧바로 번복해 재부팅을 하지 않기로 했다. 이전 줄을 고치지 않고 이 줄을 더한다. Q2의 값(앱 1.5GB, DB 1.5GB)은 그대로이고 적용만 미룬다. #4-1은 메모리 제한 없이 배포만 확인하고, 적용 방법은 Q5로 남긴다. 기록 위치: `RESEARCH.md` 6.2

## 작업 단위

### #4-1 Pi에 앱과 PostgreSQL 배포
- 상태: 진행 중
- 동작: 기록된 배포 명령을 실행하면 Pi에서 앱과 PostgreSQL이 뜨고, 맥북에서 LAN으로 보낸 `/query`가 200을 반환한다.
- 확인 방법: 맥북에서 세 프로파일의 `/query`, `/ping`, `/actuator/info`가 응답하고, `/actuator/info`의 `availableProcessors`와 JDK가 기대값과 같다.
- 필요한 결정: Q1, Q2 (2026-11-01 결정됨)
- 할 일:
  - [ ] Pi에서 명령을 실행하기 전에 연구자에게 알린다 (`AGENTS.md` 6장). 매 명령에 적용한다.
  - [x] Pi에 Docker와 Compose를 고정 버전으로 설치한다 (2026-11-16, 결정 기록 참고)
  - [x] arm64 이미지와 메모리 제한을 넣은 Pi용 Compose를 만든다 (`infra/compose.pi.yaml`, 맥북에서 `config`만 확인)
  - [ ] 맥북에서 `afe7411` 기준 arm64 이미지를 JDK 21과 25로 빌드해 태그한다 (Docker Desktop이 꺼져 있어 못 했다)
  - [ ] 이미지와 Compose 파일, `infra/db/init.sql`을 Pi로 전송하고 기동한다
  - [ ] 맥북에서 세 프로파일의 `/ping`, `/query`, `/actuator/info`를 확인한다. `availableProcessors`는 기록만 한다.
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
- 상태: 완료 (2026-11-05)
- 동작: 기록된 맥북용 Compose 명령으로 기동하면 Prometheus가 맥북에서 도는 로컬 앱의 `/actuator/prometheus`를 수집하고, Grafana에서 `hikaricp_connections_*` 지표를 볼 수 있다. 수집 대상 주소는 설정으로 바꿀 수 있어야 한다.
- 확인 방법: 맥북에서 로컬 앱을 띄우고 k6로 부하를 걸었을 때, Prometheus의 타깃 상태가 정상이고 `hikaricp_connections_active`가 바뀐다.
- 필요한 결정: 없음. 단, 맥북용 Prometheus와 Grafana 이미지는 새 의존성이므로 시작할 때 연구자에게 먼저 묻는다(`AGENTS.md` 8장).
- 할 일:
  - [x] 이미지 태그를 고정한 맥북용 Compose와 수집 설정을 만든다
  - [x] 명령을 `AGENTS.md` 6장과 `README.md`에 기록한다

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

### 핸드오프 (2026-11-05, #4-3)
- 한 일: 맥북용 Prometheus와 Grafana를 만들고 로컬 앱 수집을 확인했다.
  - [infra/compose.monitoring.yaml](../infra/compose.monitoring.yaml): Prometheus v3.15.1과 Grafana 13.3.0을 digest까지 고정해서 띄운다.
    - 역할과 기능: `SCRAPE_TARGET`(host:port)의 `/actuator/prometheus`를 5초마다 수집하고, 앱과 DB는 띄우지 않는다. `SCRAPE_TARGET`이 없으면 기동이 실패한다.
    - 쓰이는 곳: 이번 확인에서 로컬 앱을 수집했다. Pi 앱 수집(#4-5)은 미연결이다.
    - 없으면 안 되는 이유: 이 파일이 없으면 부하 중 `hikaricp_connections_active`가 시간에 따라 쌓이지 않아서 풀이 포화되는 시점을 볼 수 없다.
    - 전/후 비교: 이전에는 모니터링 구성이 없었고, 이후에는 명령 한 줄로 Prometheus와 Grafana가 뜬다.
    - 처음에는 Prometheus 설정을 별도 파일로 두려 했다. 그러나 v3.15.1이 `--enable-feature=expand-environment-variables`를 "Unknown option"으로 무시해서 `${SCRAPE_TARGET}`가 치환되지 않았다. 그래서 설정을 Compose의 `configs.content`에 인라인했다.
  - [infra/grafana/](../infra/grafana/)에서 데이터소스를 프로비저닝하고, active, idle, pending, max를 보여 주는 "HikariCP 풀" 대시보드를 등록했다.
  - `AGENTS.md` 6장과 `README.md`에 명령과 고정 버전을 기록했다.
  - 확인은 로컬 맥북에서 앱 `platform` 프로파일, `POOL_SIZE=10`, JDK 21로 했다. Prometheus 타깃은 `up`이었다. k6 `sleep50`을 `RATE=100`으로 25초 걸었을 때 `hikaricp_connections_active`는 5초 간격 표본에서 5가 다섯 번 나온 뒤 0이 되었다. Grafana 대시보드 등록과 Grafana를 거친 Prometheus 질의도 응답했다. 이 값은 개발 중 숫자라서 `results/`에 넣지 않았다.
- 남은 일: #4-1, #4-2, #4-4, #4-5. 모두 Pi가 필요하다.
- 막힌 점 / 확인 필요: Pi가 아직 없다. 스크랩이 Pi 앱 부하에 미치는 영향은 측정하지 않았다. Grafana 대시보드를 브라우저로 직접 보지는 않았고 API로만 확인했다.
- 다음 세션 첫 작업: Pi를 쓸 수 있으면 #4-1을 시작한다(연구자가 방열, 전원, 유선 LAN을 점검하고 접속 정보를 알려 준다).

### 핸드오프 (2026-11-16, #4 문서 정합성 정리)
- 한 일: 단위 작업은 하지 않았고, #4-3 이후의 문서 정합성 확인과 정리만 했다. `app/`, `load/`, `infra/`는 바꾸지 않았다.
  - [RESEARCH.md](../RESEARCH.md)를 v0.11로 올려서 #4-3의 결정을 기록했다. 6.9에는 Prometheus v3.15.1과 Grafana 13.3.0의 digest 고정, 스크랩 주기 5초, 타임아웃 3초를 적었다. 10.1 (6)에는 arm64 확인 결과와 환경변수 치환이 불가능하다는 사실을 적었다. 13장의 맥북용 Compose 설명에서는 k6를 뺐다.
  - [issues/05-pilot-scripts.md](05-pilot-scripts.md)의 선행 표기를 `#3 (완료), #4 (진행 중)`으로 고쳤다.
  - `.vscode/`를 [.gitignore](../.gitignore)에 추가했다.
  - 연구자가 Pi를 쓸 수 있게 될 때까지 #4를 중단하기로 했다(2026-11-06 결정). 이 결정은 #4-1의 시작 조건과 같아서 별도 결정 기록은 만들지 않았다.
- 남은 일: #4-1, #4-2, #4-4, #4-5. 모두 Pi가 필요하다.
- 막힌 점 / 확인 필요:
  - Pi가 아직 없다.
  - 작업 트리에 이번 작업과 무관한 미추적 항목이 생겼다. `.DS_Store` 세 개(저장소 루트 아래 `app/`, `infra/` 포함)와 `app/bin/`이다. `app/bin/`은 IDE가 만든 빌드 산출물로 보이지만 확인하지 않았다. `.gitignore`에 추가할지는 연구자가 정한다.
  - 푸시 여부는 확인하지 않았다.
- 다음 세션 첫 작업: Pi를 쓸 수 있게 되면 #4-1을 시작한다. 연구자가 방열, 전원, 유선 LAN을 점검하고 접속 정보(SSH 호스트명 또는 IP, 계정)를 알려 준다. Pi에서 명령을 실행하기 전에는 연구자에게 먼저 알린다.

### 핸드오프 (2026-11-16, #4-1 진행 중)
- 한 일: Pi를 쓸 수 있게 되어 #4-1을 시작했고, Pi에 Docker를 설치하고 Pi용 Compose를 작성했다. 앱 이미지 빌드부터는 하지 못했다. 앱 코드와 설정값은 바꾸지 않았다.
  - 연구자가 Pi에 SSH로 접속해 있고, 접속 정보는 연구자가 직접 입력한다. 키는 등록하지 않았다. Pi 명령은 내가 제시하고 연구자가 실행해 출력을 붙여 넣는 방식이다.
  - Pi 환경 확인(읽기 전용 명령): Debian 13(trixie) aarch64, 커널 6.18.39+rpt-rpi-v8, 4코어, 메모리 3.7GiB, 스왑 2GiB, 루트 microSD 29GB 중 18GB 여유, 47.7℃, `get_throttled=0x0`.
  - Pi에 Docker 29.9.2, Compose v5.6.1, containerd.io 2.4.2-4를 `apt-mark hold`로 고정해 설치했다. `docker info`에서 `Cgroup Version: 2`, `Cgroup Driver: systemd`, `Storage Driver: overlayfs`를 확인했다. `hello-world`는 `latest` 태그라 실행하지 않았다.
  - memory cgroup이 꺼져 있다는 것을 확인했다. `docker info`가 "No memory limit support"와 "No swap limit support"를 경고했고, `/proc/cmdline`에 `cgroup_disable=memory`가 있었고, `cgroup.controllers`가 `cpuset cpu io pids`였다. `cpuset`은 쓸 수 있다. 연구자가 재부팅을 하지 않기로 해서 메모리 제한은 적용하지 못한 채 두고(Q5), #4-1은 제한 없이 배포만 확인하기로 했다.
  - [infra/compose.pi.yaml](../infra/compose.pi.yaml) (신규, 미커밋)
    - 역할과 기능: 기본 [infra/compose.yaml](../infra/compose.yaml) 위에 겹쳐 쓰는 Pi용 파일이다. 앱 이미지를 `APP_IMAGE`(필수)로 받고, 기본 파일의 `build`를 지우고(`!reset null`), 앱 포트를 `9080:8080`으로 바꾸고(`!override`), DB 포트를 열지 않는다(`!reset []`). 앱과 DB에 `mem_limit: 1536m`을 지정한다. `cpuset`은 없다. 설정값과 쿼리는 건드리지 않는다.
    - 쓰이는 곳: 미연결이다. 맥북에서 `docker compose config`로 병합 결과만 확인했다(앱 포트 `9080→8080` 하나, DB 포트 없음, `mem_limit` 1610612736바이트, `APP_IMAGE`가 없으면 실패). Pi에서는 아직 실행하지 않았다.
    - 없으면 안 되는 이유: 이 파일이 없으면 Pi에서 기본 파일의 `build: ../app`이 소스가 없는 경로를 찾다 실패하고, 8080을 공개하려다 막힌다.
    - 전/후 비교: 이전에는 로컬용 Compose뿐이었고, 이후에는 Pi용 덮어쓰기 파일이 있다.
    - 공정성과 변수 영향: 정책은 포트와 메모리 제한이 실험 변수가 아니라는 것이다. 시나리오는 세 프로파일이 같은 포트 매핑을 쓰고 컨테이너 안의 8080과 앱 설정이 그대로라는 것이다. 코드는 `ports: !override`가 호스트 쪽만 바꾼다. `mem_limit`은 위 이유로 지금 적용되지 않는다.
  - [infra/compose.yaml](../infra/compose.yaml)은 1행 주석만 실제 동작에 맞게 고쳤다. 설정은 그대로다.
  - `./gradlew test bootJar`는 `BUILD SUCCESSFUL`이었으나 모든 작업이 UP-TO-DATE였다. 테스트를 이번에 새로 실행한 것이 아니라 캐시된 결과이고, jar는 2026-11-05 11:29에 만든 것이다. 앱 소스는 바꾸지 않았다.
  - [AGENTS.md](../AGENTS.md) 11장에 "명령어와 코드 설명" 규칙을 추가했다(연구자 요청). [RESEARCH.md](../RESEARCH.md)는 v0.12로 올려 6.2에 Pi 환경, 포트, 배포 방식, memory cgroup 확인 결과를 기록했다. [README.md](../README.md)의 고정 버전에 Pi 환경을 더했다.
- 남은 일: #4-1의 나머지(이미지 빌드, 전송, 기동, 확인, 명령 기록), #4-2, #4-4, #4-5, Q5.
- 막힌 점 / 확인 필요:
  - 맥북의 Docker Desktop이 꺼져 있다. `docker build`가 `failed to connect to the docker API at unix:///Users/shootingstar/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/shootingstar/.docker/run/docker.sock: connect: no such file or directory`로 실패했다. 연구자가 Docker Desktop을 켜야 한다.
  - Pi의 `/boot/firmware/cmdline.txt`가 수정되었는지 모른다. `cgroup_enable=memory`를 더하는 `sed` 명령을 안내했으나 연구자가 실행했는지 답이 없다. 수정되어 있으면 다음 재부팅 때 적용되므로 `cat /boot/firmware/cmdline.txt; ls ~/cmdline.txt.bak`로 확인해야 한다.
  - `mem_limit`이 memory cgroup이 꺼진 Pi에서 컨테이너 기동을 막는지는 확인하지 못했다. 막으면 에러 원문을 연구자에게 보이고 처리 방법을 묻는다.
  - `cgroup_disable=memory`가 `cgroup_enable=memory`와 함께 있을 때 어느 쪽이 적용되는지, 주입 경로가 무엇인지는 확인하지 못했다(Q5).
  - 연구자가 8080을 쓸 수 없는 이유를 밝히지 않았다.
  - 푸시 여부는 확인하지 않았다. 작업 트리에 `AGENTS.md`, `STATUS.md`, `README.md`, `RESEARCH.md`, `infra/compose.yaml`, 이 이슈 파일의 변경과 `infra/compose.pi.yaml`(신규), `.DS_Store`, `app/bin/`이 미커밋이다.
- 다음 세션 첫 작업: 연구자가 맥북의 Docker Desktop을 켠 뒤 `cmdline.txt` 상태를 알려 준다. 그다음 `git rev-parse --short HEAD`로 태그 해시를 정하고(지금은 `afe7411`이지만 커밋하면 바뀐다) `docker build --platform linux/arm64 --build-arg JDK=<21|25> -t poolsweep-app:<해시>-jdk<JDK> app`으로 이미지를 만든 뒤, `docker save`로 묶어 Pi에 전송하는 명령을 연구자에게 먼저 알리고 승인받아 진행한다.
