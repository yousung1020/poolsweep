# #4 Pi 배포와 코어 분리

상태: 대기

> 2026-10-23에 GitHub 이슈 #4에서 이 파일로 옮겼다. 원본 이슈는 삭제했다.

## 목표

`RESEARCH.md` 6.2를 구현한다.

## 체크리스트

- [ ] Pi용 Compose: 앱 코어 0-1, PostgreSQL 코어 2-3 (`cpuset`), arm64 이미지, 메모리 제한
- [ ] Pi OS에서 cgroup cpuset이 켜져 있어 `cpuset`이 실제로 적용되는지 확인한다 (#1에서 넘어옴)
- [ ] 맥북용 Compose: Prometheus + Grafana, Pi 원격 스크랩
- [ ] 온도와 스로틀링(`vcgencmd get_throttled`) 기록 방법을 정한다
- [ ] Pi에서 명령을 실행하기 전에 연구자에게 알린다 (`CLAUDE.md` 6장)

선행 조건: 연구자의 Pi 방열, 전원, 유선 LAN 점검

## 핸드오프

(아직 없음)
