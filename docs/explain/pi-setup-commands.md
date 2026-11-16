# Pi 설정 명령 해설

2026-11-16에 Raspberry Pi에서 실행한 명령이 각각 무엇을 하는지, 왜 필요했는지, 출력을 어떻게 읽는지를 정리한 문서입니다. 설계와 결정의 기준은 [RESEARCH.md](../../RESEARCH.md) 6.2, 작업 맥락과 결정 기록은 [issues/04-pi-deploy.md](../../issues/04-pi-deploy.md)이며, 이 문서는 그 내용을 풀어 설명하는 보조 자료입니다. 앞으로 Pi 명령이 늘어나면 같은 형식으로 이어서 추가합니다.

## 0. 먼저 알아 둘 것

- **읽기 전용 명령과 변경 명령**: 상태를 보기만 하는 명령은 Pi를 바꾸지 않습니다. `apt install`, `apt-mark`, `tee`, `sed -i`, `reboot`처럼 파일이나 설정을 바꾸는 명령은 측정 환경에 영향을 줄 수 있어서 실행 전에 따로 확인을 받았습니다.
- **`sudo`**: 관리자(root) 권한으로 명령을 실행합니다. 패키지 설치나 시스템 파일 수정에 필요합니다.
- **`apt`**: Debian 계열 OS의 패키지 관리자입니다. 프로그램을 저장소에서 받아 설치하고 버전을 관리합니다.
- **왜 신경 쓰는가**: 이 프로젝트는 "같은 조건에서 비교"하는 실험입니다. Pi의 OS, 도구 버전, 설정이 실행마다 달라지면 결과의 차이가 코드 때문인지 환경 때문인지 알 수 없습니다. 그래서 환경을 확인하고, 버전을 고정하고, 기록합니다.

## 1. Pi의 현재 상태 확인 (읽기 전용)

한 줄에 묶어서 실행했던 명령입니다.

```
cat /etc/os-release; uname -a; nproc; free -h; df -h /; docker --version; vcgencmd measure_temp; vcgencmd get_throttled
```

세미콜론(`;`)은 앞 명령의 성공 여부와 관계없이 다음 명령을 이어서 실행하라는 뜻입니다.

- `cat /etc/os-release`는 OS 종류와 버전을 보여 줍니다. 결과는 Debian 13(trixie)였습니다. Docker 설치 절차가 OS 종류와 코드명(`trixie`)에 따라 달라서 가장 먼저 확인했습니다.
- `uname -a`는 커널 버전과 CPU 구조를 보여 줍니다. 결과의 `aarch64`는 64비트 ARM이라는 뜻이며, 앱 이미지를 맥북에서 arm64로 빌드해야 하는 근거가 됩니다.
- `nproc`는 쓸 수 있는 CPU 코어 수입니다. 결과는 4였습니다. 앱을 코어 0-1, DB를 코어 2-3에 나누는 계획(#4-2)의 전제입니다.
- `free -h`는 메모리 사용 현황입니다. `-h`는 GiB 같은 읽기 쉬운 단위로 보여 달라는 옵션입니다. 총 메모리는 3.7GiB, 스왑은 2.0GiB였습니다. 스왑은 메모리가 모자랄 때 디스크를 임시 메모리로 쓰는 영역이며, 디스크가 훨씬 느려서 켜져 있으면 측정 지연에 끼어들 수 있습니다. 그래서 값을 기록해 두었습니다.
- `df -h /`는 루트 파일 시스템의 용량입니다. 29GB 중 18GB가 비어 있었고, 장치 이름 `mmcblk0p2`는 microSD 카드라는 뜻입니다. 이미지 몇 개를 올리기에 충분한지 확인하는 용도였습니다.
- `docker --version`은 Docker 설치 여부를 확인합니다. `명령어를 찾을 수 없음`이 나왔으므로 설치가 필요했습니다. 이 오류는 정상입니다.
- `vcgencmd measure_temp`는 Pi의 현재 온도입니다. 47.7℃는 아무 일도 하지 않을 때의 값입니다.
- `vcgencmd get_throttled`는 Pi가 성능을 강제로 낮춘 적이 있는지 보여 줍니다. `vcgencmd`는 Pi 전용 도구입니다. `0x0`은 전원 부족이나 과열로 인한 제한이 기록되지 않았다는 뜻입니다. 0이 아닌 값의 비트별 의미는 이 세션에서 공식 문서로 다시 확인하지 않았으니, 값이 나오면 그때 확인합니다. 실험 중 스로틀링이 생기면 그 실행은 무효 후보로 표시합니다(`RESEARCH.md` 6.2).

## 2. Docker 설치 준비

### 2.1 충돌 패키지 확인 (읽기 전용)

```
dpkg --get-selections docker.io docker-compose docker-doc docker-buildx podman-docker containerd runc
```

OS 기본 저장소에도 이름이 비슷한 Docker 패키지가 있습니다. 공식 패키지와 섞이면 충돌하므로 미리 설치되어 있는지 확인합니다. `no packages found`가 나오면 아무것도 없다는 뜻이고, 제거할 것이 없다는 의미입니다.

### 2.2 Docker 공식 저장소 등록 (Pi를 변경)

OS 기본 저장소의 Docker는 버전이 오래되거나 맥북과 다를 수 있어서 Docker 공식 저장소를 등록해 원하는 버전을 고릅니다.

```
sudo apt update
sudo apt install ca-certificates curl
```

- `apt update`는 **설치가 아니라** 어떤 패키지가 어떤 버전으로 있는지 목록을 새로 받는 일입니다.
- `ca-certificates`는 HTTPS 인증서를 확인하는 데 쓰고, `curl`은 파일 다운로드 도구입니다. 아래 키 다운로드에 필요합니다.

```
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
```

- `install -d`는 디렉터리를 만들고, `-m 0755`는 소유자만 쓰고 모두가 읽을 수 있는 권한입니다.
- `curl`은 Docker가 배포 패키지에 서명할 때 쓰는 공개 키를 받습니다. 옵션은 순서대로 오류 시 실패(`-f`), 진행 표시 생략(`-s`), 오류는 표시(`-S`), 이동 주소 따라가기(`-L`)이며 `-o`는 저장할 파일입니다. 이 키가 있어야 받은 패키지가 정말 Docker가 만든 것인지 검증할 수 있습니다.
- `chmod a+r`은 모든 사용자(`a`)가 읽을 수 있게(`r`) 합니다. apt가 이 키를 읽어야 하기 때문입니다.

```
sudo tee /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/debian
Suites: $(. /etc/os-release && echo "$VERSION_CODENAME")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt update
```

- 이 블록은 apt에게 "이 주소의 저장소도 보라"고 알려 주는 설정 파일을 만듭니다. `<<EOF ... EOF`는 그 사이의 줄을 그대로 입력으로 넘기는 문법이고, `tee`는 그 입력을 root 권한으로 파일에 저장합니다. (`sudo`와 `>`를 함께 쓰면 권한이 맞지 않는 문제가 있어 `tee`를 씁니다.)
- `$(...)` 안의 명령은 실행되어 결과로 바뀝니다. 여기서는 `trixie`와 `arm64`로 채워집니다.
- `Signed-By`는 위에서 받은 키로만 이 저장소의 패키지를 검증하라는 뜻입니다.
- 마지막 `apt update`는 새 저장소의 목록을 받습니다. 출력에 `download.docker.com`이 보이면 정상입니다.

## 3. 버전 고르기와 설치

### 3.1 설치 가능한 버전 조회 (읽기 전용)

```
apt list --all-versions docker-ce
apt list --all-versions docker-compose-plugin containerd.io 2>/dev/null | head -12
apt list --all-versions docker-compose-plugin 2>/dev/null | head -8
```

- `--all-versions`는 최신 하나가 아니라 설치할 수 있는 모든 버전을 보여 줍니다. 특정 버전을 고르기 위해서입니다.
- `2>/dev/null`은 오류 메시지를 버리고, `| head -12`는 앞의 12줄만 보여 줍니다. 목록이 너무 길어서 붙였지만, 두 패키지를 한꺼번에 조회하는 바람에 12줄이 `containerd.io`로 다 차서 `docker-compose-plugin`이 보이지 않았습니다. 그래서 세 번째 줄처럼 따로 다시 조회했습니다.
- 버전 문자열 `5:29.9.2-1~debian.13~trixie`의 구조는 다음과 같습니다. `5:`는 패키지 시스템 내부의 버전 체계 번호, `29.9.2`는 Docker 버전, `-1`은 패키지를 다시 만든 횟수, `~debian.13~trixie`는 대상 OS입니다.
- 맥북의 Docker와 같은 29.9.2, Compose v5.6.1을 고른 이유는 두 환경의 차이를 줄이기 위해서입니다.

### 3.2 설치와 버전 고정 (Pi를 변경)

```
VERSION_STRING=5:29.9.2-1~debian.13~trixie
sudo apt install --no-install-recommends docker-ce=$VERSION_STRING docker-ce-cli=$VERSION_STRING containerd.io=2.4.2-4~debian.13~trixie docker-compose-plugin=5.6.1-1~debian.13~trixie
sudo apt-mark hold docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

- 첫 줄은 셸 변수에 버전을 저장해 두 번 입력하는 오타를 막습니다.
- 패키지 이름 뒤의 `=버전`은 그 버전만 설치하라는 뜻입니다. 이렇게 하지 않으면 최신이 설치되어 재현성이 깨집니다.
- 설치한 네 가지는 Docker 서버(`docker-ce`), 명령행 도구(`docker-ce-cli`), 컨테이너를 실제로 실행하는 런타임(`containerd.io`), `docker compose` 명령(`docker-compose-plugin`)입니다.
- `--no-install-recommends`는 "권장" 패키지를 자동으로 끌어오지 않는 옵션입니다. 이를 쓰지 않으면 이미지 빌드 도구(buildx)가 버전 고정 없이 따라 들어옵니다. 이 Pi에서는 이미지를 빌드하지 않으므로 필요 없습니다.
- `apt-mark hold`는 이 패키지들을 보류 상태로 만들어, 나중에 `apt upgrade`를 해도 버전이 바뀌지 않게 합니다. 측정 도중 Docker가 몰래 업데이트되는 일을 막습니다.

## 4. 설치 확인 (읽기 전용)

```
sudo systemctl is-active docker
sudo docker version
sudo docker compose version
sudo docker info | grep -E "Server Version|Cgroup|Storage Driver|Operating System|Total Memory|CPUs"
dpkg-query -W -f='${Package} ${Version}\n' docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

- `systemctl is-active docker`는 Docker 서비스가 실행 중인지 보여 줍니다. `active`면 정상입니다.
- `docker version`은 클라이언트(명령)와 서버(실행하는 쪽)의 버전을 둘 다 보여 줍니다. 서버 출력이 없으면 서비스가 떠 있지 않다는 뜻입니다. 둘 다 29.9.2였습니다.
- `docker compose version`은 Compose 버전입니다. v5.6.1이었습니다.
- `docker info | grep ...`은 Docker가 인식한 환경 정보 중 필요한 줄만 골라 봅니다. `|`는 앞 명령의 출력을 뒤 명령에 넘기는 연결선이고, `grep -E`는 여러 패턴 중 하나라도 있는 줄만 남깁니다.
- `dpkg-query`는 실제로 설치된 버전을 확인합니다. 이 출력을 [README.md](../../README.md)의 환경 기록에 옮겼습니다.
- `hello-world` 컨테이너로 동작을 확인하는 것이 흔하지만, 이 이미지는 태그가 `latest`뿐이라 버전 고정 규칙에 어긋나서 쓰지 않았습니다.

## 5. 메모리 제한이 적용되지 않는 문제

### 5.1 무엇이 이상했나

`docker info`에서 아래 경고가 나왔습니다.

```
WARNING: No memory limit support
WARNING: No swap limit support
```

컨테이너에 "메모리를 1.5GB까지만 쓰라"고 제한을 걸 수 없다는 뜻입니다. 계획(Q2)은 앱과 DB에 각각 1.5GB 제한을 거는 것이었으므로 중요한 문제입니다.

### 5.2 원인을 확인한 명령 (읽기 전용)

```
cat /proc/cmdline
cat /sys/fs/cgroup/cgroup.controllers
ls -l /boot/firmware/cmdline.txt
cat /boot/firmware/cmdline.txt
```

- **cgroup**은 리눅스가 프로세스 묶음별로 CPU, 메모리 같은 자원의 사용량을 제한하는 기능입니다. Docker의 메모리 제한과 코어 고정이 모두 이 기능 위에서 동작합니다.
- **컨트롤러**는 cgroup의 자원별 담당자입니다. `cpuset`은 어느 코어를 쓸지, `memory`는 메모리 양을 제어합니다.
- `/proc/cmdline`은 지금 부팅된 커널이 받은 시작 옵션입니다. 여기에 `cgroup_disable=memory`가 있어서 메모리 컨트롤러가 꺼진 채로 부팅했다는 것을 알 수 있습니다.
- `cgroup.controllers`는 지금 쓸 수 있는 컨트롤러 목록입니다. 결과는 `cpuset cpu io pids`였습니다. `memory`가 없으므로 경고의 원인이 확인되었습니다. 반면 `cpuset`이 있어서 코어 분리(#4-2)는 가능합니다.
- `/boot/firmware/cmdline.txt`는 부팅 시 커널에 넘기는 옵션을 적어 두는 파일입니다. 이 파일에는 `cgroup_disable=memory`가 없었는데도 `/proc/cmdline`에는 있었으므로, 펌웨어가 따로 넣는 것으로 보입니다. 주입 경로는 확인하지 못했습니다.
- Raspberry Pi 커널이 메모리 cgroup을 기본으로 꺼 둔다는 것은 [공식 커널 커밋](https://github.com/raspberrypi/linux/commit/2b13c54592135b6fab269517ed687fa9f80bf8e5)에서 확인했습니다.

### 5.3 안내했지만 실행하지 않기로 한 명령

```
sudo cp /boot/firmware/cmdline.txt ~/cmdline.txt.bak
sudo sed -i '1 s/$/ cgroup_enable=memory/' /boot/firmware/cmdline.txt
sudo reboot
```

- 첫 줄은 원본을 백업하고, 둘째 줄은 `cmdline.txt`의 첫 줄 끝에 `cgroup_enable=memory`를 붙입니다. 파일은 반드시 한 줄이어야 해서 줄바꿈이 생기면 안 됩니다. 셋째 줄은 재부팅이며, 커널 옵션은 부팅 때만 적용되기 때문에 필요합니다.
- 연구자가 재부팅을 하지 않기로 했으므로 #4-1은 메모리 제한 없이 진행합니다. 이 방법이 통하는지(`cgroup_disable=memory`가 이미 있는 상태에서 `cgroup_enable=memory`가 우선하는지)는 확인하지 못했고, 열린 질문 Q5로 남아 있습니다.
- 앞의 두 줄을 실행했는지는 알 수 없습니다. 실행했다면 다음 재부팅 때 적용되므로 `cat /boot/firmware/cmdline.txt`로 확인하고, 원하지 않으면 `sudo cp ~/cmdline.txt.bak /boot/firmware/cmdline.txt`로 되돌립니다.

## 6. Pi용 Compose 파일이 하는 일

Pi에서 실행하는 명령은 아니지만 이 설정과 이어지는 파일입니다. [infra/compose.pi.yaml](../../infra/compose.pi.yaml)은 로컬용 [infra/compose.yaml](../../infra/compose.yaml) 위에 겹쳐 쓰는 파일입니다.

- **포트 `9080:8080`**: 왼쪽은 Pi 바깥에서 접속하는 번호, 오른쪽은 컨테이너 안에서 앱이 듣는 번호입니다. Pi에서 8080을 쓸 수 없어서 바깥 번호만 9080으로 바꿨고, 앱 설정은 그대로라서 세 프로파일의 조건은 같습니다.
- **DB 포트를 열지 않음**: 앱은 Compose가 만든 내부 네트워크로 DB에 접속하므로 바깥에 열 이유가 없고, 비밀번호가 약한 DB를 LAN에 노출하지 않으려는 것입니다.
- **이미지 사용**: Pi에는 앱 소스가 없으므로 맥북에서 빌드해 옮긴 이미지를 씁니다. `APP_IMAGE`를 지정하지 않으면 기동이 실패하게 해서 실수로 `latest` 같은 이미지를 쓰지 않도록 했습니다.
- **`mem_limit`**: 5장의 이유로 지금은 적용되지 않습니다.

## 7. 한눈에 보는 정리

- 읽기 전용: `cat`, `uname`, `nproc`, `free`, `df`, `vcgencmd`, `dpkg --get-selections`, `apt list`, `docker version`과 `docker info`, `dpkg-query`
- Pi를 바꾼 것: apt 저장소와 키 등록, Docker 설치와 버전 고정(`apt install`, `apt-mark hold`)
- 안내했지만 실행하지 않기로 한 것: `cmdline.txt` 수정과 재부팅
- 확인 필요: `cmdline.txt`의 현재 수정 여부, `cgroup_enable=memory`로 메모리 컨트롤러가 켜지는지(Q5)
