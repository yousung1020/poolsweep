# #3 로컬 Compose + k6 최소 스크립트

상태: 대기

> 2026-10-23에 GitHub 이슈 #3에서 이 파일로 옮겼다. 원본 이슈는 삭제했다.

## 목표

`RESEARCH.md` 6.4와 13장 3~4번을 구현한다.

## 체크리스트

- [ ] 로컬 PostgreSQL + 앱 Compose를 만든다 (이미지 태그 고정, `latest` 금지)
- [ ] PostgreSQL 메이저 버전과 이미지 태그를 정한다 (확인 시점 최신 패치: 18.6, 17.11. `RESEARCH.md` 10.1 (6))
- [ ] k6 E1(arrival-rate) 최소 스크립트를 만들고 `dropped_iterations`와 `/ping` 기준선을 기록한다
- [ ] `maxVUs`와 요청 타임아웃을 정한다. D1 기준(도착률 × 요청 타임아웃)에 `connectionTimeout` 30초를 넣으면 500건/초에서 15,000 VU가 된다. 맥북 k6가 감당하는지 확인한다 (`RESEARCH.md` 10.1 (5))
- [ ] k6는 시드 범위(1~10000)의 id만 보낸다. 없는 id면 `pg_sleep`이 실행되지 않아 지연 쿼리가 짧아진다 (#2에서 확인)
- [ ] 로컬 숫자는 `results/`에 넣지 않는다

## 핸드오프

(아직 없음)
