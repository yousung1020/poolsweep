# 맥북 Prometheus와 Grafana 해설 (#4-3)

[infra/compose.monitoring.yaml](../../infra/compose.monitoring.yaml)은 맥북에서 Prometheus와 Grafana를 띄워 앱의 HikariCP 풀 지표를 모으고 그래프로 보여 줍니다. 측정 환경인 Pi에는 올리지 않고, 맥북이 Pi 앱을 원격으로 수집합니다(1행, RESEARCH.md 6.2).

## 1. 큰 그림과 용어

- **Prometheus**는 지정한 주소를 일정 주기로 호출해서 숫자(지표)를 시계열로 쌓는 도구입니다. 이 호출을 스크랩이라고 부릅니다.
- **Grafana**는 Prometheus에 쌓인 시계열을 그래프로 보여 주는 화면입니다.
- 앱은 `/actuator/prometheus`에서 `hikaricp_connections_*` 지표를 내보냅니다([application.yaml](../../app/src/main/resources/application.yaml#L18)). 풀이 포화되는 시점을 시간 축에서 보려는 것이 목적입니다.
- 흐름은 이렇습니다.
  1. 앱이 `/actuator/prometheus`로 풀 상태를 노출합니다.
  2. Prometheus가 5초마다 그 주소를 가져가 저장합니다.
  3. Grafana가 Prometheus에 질의해 그래프를 그립니다.

## 2. compose.monitoring.yaml

- **이미지 고정(8, 17행)**: Prometheus v3.15.1과 Grafana 13.3.0을 태그와 digest로 고정했습니다. 이유는 [로컬 Compose 해설](local-compose-and-k6.md)의 DB 이미지와 같습니다.
- **포트(14, 28행)**: `127.0.0.1:9090`과 `127.0.0.1:3000`처럼 앞에 `127.0.0.1`을 붙여 맥북 밖으로는 열지 않습니다. 같은 LAN의 다른 기기에서 접속할 수 없게 하려는 것입니다.
- **Grafana 설정(18~23행)**: 맥북 로컬에서만 보는 실험용이라 로그인 없이 읽기 전용(`Viewer`)으로 엽니다. 사용 통계 전송과 업데이트 확인은 끕니다.
- **프로비저닝(25~26행)**: 데이터소스와 대시보드를 파일로 미리 등록해 컨테이너가 뜨면 바로 그래프가 보이게 합니다. 손으로 화면을 설정하는 단계를 없애려는 것입니다(`AGENTS.md` 4장).
- **기동 순서(29~30행)**: Prometheus가 먼저 뜬 뒤 Grafana가 뜹니다.

## 3. 수집 설정을 Compose 안에 넣은 이유

- 설정은 `configs.content`에 직접 들어 있습니다(32~50행). 처음에는 별도 파일로 두려 했습니다.
- 수집 대상 주소를 실행할 때 바꿀 수 있어야 해서 환경변수 치환이 필요했는데, Prometheus 설정 파일은 환경변수를 읽지 못합니다. v3.15.1에는 치환 기능을 켜는 옵션(`--enable-feature=expand-environment-variables`)도 없어서 "Unknown option"으로 무시되었습니다(33~34행).
- 그래서 Compose가 `content` 안의 `${...}`를 치환하게 했습니다. 50행의 `${SCRAPE_TARGET:?...}`는 값이 없으면 기본값으로 뜨는 대신 실패합니다. 수집 대상이 엉뚱한 곳을 향하는 실수를 막으려는 것입니다.

## 4. 고정값 두 개: 5초와 3초

- `scrape_interval: 5s`(41행): 짧은 k6 실행에서도 `hikaricp_connections_active`가 바뀌는 모습을 볼 수 있으면서, Pi 앱에는 초당 0.2건만 가는 값입니다(38행).
- `scrape_timeout: 3s`(42행): 주기보다 짧아야 하며, 응답이 늦어도 다음 스크랩과 겹치지 않게 했습니다(39행).
- 둘 다 실험 변수가 아니라 고정값이며 실험 중에 바꾸지 않습니다(37행, 2026-11-05 연구자 결정).
- `metrics_path: /actuator/prometheus`(46행)는 Prometheus의 기본 경로(`/metrics`)와 달라서 지정해야 합니다.

## 5. Grafana 대시보드

- [prometheus-datasource.yaml](../../infra/grafana/provisioning/datasources/prometheus-datasource.yaml#L8)는 같은 Compose 네트워크 안의 서비스 이름(`http://prometheus:9090`)으로 접근합니다.
- [dashboards.yaml](../../infra/grafana/provisioning/dashboards/dashboards.yaml#L6)는 `/var/lib/grafana/dashboards`의 파일을 대시보드로 읽게 합니다.
- [hikaricp.json](../../infra/grafana/dashboards/hikaricp.json#L16)은 패널 하나에 네 가지 지표를 겹쳐 그립니다. 읽는 법은 다음과 같습니다.
  - `active`(16행)는 지금 쓰이는 커넥션 수입니다.
  - `idle`(17행)은 놀고 있는 커넥션 수입니다.
  - `pending`(18행)은 풀이 비어서 커넥션을 기다리는 스레드 수입니다. 이 값이 올라가면 풀이 병목이라는 신호입니다.
  - `max`(19행)는 풀 크기의 상한입니다. `active`가 `max`에 붙어 있고 `pending`이 늘면 풀이 포화된 것입니다.
- 6~7행은 5초마다 갱신하고 최근 5분을 보여 줍니다. 스크랩 주기와 같습니다.

## 6. 알아 둘 점

- 이 확인은 맥북의 로컬 앱을 대상으로 했습니다. Pi 앱 수집은 #4-5입니다.
- **Pi 수집 주소는 9080입니다.** Pi에서는 앱을 호스트 포트 9080으로 공개하기로 결정(2026-11-16)했으므로 Pi를 수집할 때는 `SCRAPE_TARGET=<Pi 주소>:9080`을 씁니다. 맥북의 로컬 앱은 그대로 `host.docker.internal:8080`입니다. 이 값은 [compose.monitoring.yaml](../../infra/compose.monitoring.yaml#L49), [README.md](../../README.md), [AGENTS.md](../../AGENTS.md) 6장에 맞춰 두었습니다.
