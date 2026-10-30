#!/usr/bin/env bash
# k6 공식 바이너리를 버전과 SHA-256으로 고정해 load/.bin/k6에 설치한다(#3 Q6).
# brew는 버전을 고정할 수 없고, 컨테이너로 돌리면 Docker VM의 NAT와 자원 제한이 지연과 VU 수에 끼어들기 때문이다.
# 맥북(Apple Silicon) 전용이다. 버전을 바꾸면 아래 두 값을 릴리스의 checksums 파일과 함께 고친다.
set -euo pipefail

K6_VERSION=v2.3.0
K6_SHA256=b2417a3038edc5fe81dc178a889237724b595c5c9cfed875822008e46e862c7d  # k6-v2.3.0-macos-arm64.zip

if [[ "$(uname -s)-$(uname -m)" != "Darwin-arm64" ]]; then
  echo "이 스크립트는 macOS arm64 전용입니다: $(uname -s)-$(uname -m)" >&2
  exit 1
fi

dir="$(cd "$(dirname "$0")" && pwd)/.bin"
name="k6-${K6_VERSION}-macos-arm64"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

curl -fsSL -o "$tmp/$name.zip" "https://github.com/grafana/k6/releases/download/${K6_VERSION}/${name}.zip"
echo "${K6_SHA256}  $tmp/$name.zip" | shasum -a 256 -c -
unzip -q "$tmp/$name.zip" -d "$tmp"
mkdir -p "$dir"
install -m 0755 "$tmp/$name/k6" "$dir/k6"
"$dir/k6" version
