#!/bin/sh
# Builds the debug APK on any Linux host with a JDK and the Android SDK:
#
#   ./build-apk.sh
#
# The Android SDK comes from ANDROID_HOME (or ANDROID_SDK_ROOT, or sdk.dir in
# AndorsTrail/local.properties). Prints the APK path.
#
# The Gradle daemon must run on JetBrains JDK 21
# (AndorsTrail/gradle/gradle-daemon-jvm.properties). Gradle provisions a glibc
# build of it, which does not start on musl hosts such as Alpine. There the
# script downloads JetBrains' own musl build once into
# ${XDG_CACHE_HOME:-~/.cache}/build-apk/jdk, checks it against the SHA-512
# recorded below (the .checksum file JetBrains publishes next to it) and points
# Gradle at it. The runtime-only "jbr" archive is not enough: Gradle accepts
# only the full JDK ("jbrsdk").
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && ! grep -q '^sdk\.dir=' "$ROOT/AndorsTrail/local.properties" 2>/dev/null; then
  echo "✗ no Android SDK: set ANDROID_HOME or sdk.dir in AndorsTrail/local.properties"
  exit 2
fi

is_musl() {
  for f in /lib/ld-musl-*; do [ -e "$f" ] && return 0; done
  return 1
}

# Download URL $1 to file $2 with curl, or busybox wget when curl is missing.
dl() {
  if command -v curl >/dev/null 2>&1; then curl -fsSL -o "$2" "$1"; else wget -q -O "$2" "$1"; fi
}

JBR_V=21.0.11
JBR_B=b1163.116
# Prints the directory of the musl JetBrains JDK, downloading it on first use.
jbr_musl() {
  case "$(uname -m)" in
    aarch64) a=aarch64; sum=b295ac7bff09f964dcea3e63fb0f2e34a1f3f972f3ba0fc0b2b20512dc18faae8c83d194aa293618b3a2487d167dec55e432ab5a2af19565f4979624fe34de5d ;;
    x86_64) a=x64; sum=0020a0ddca442e4078d8e3ffcc0a6d71e032f33e597196dc6d779caa4b5a64576d6f86428edfe5433fd72567f88e39da4fc1bc1d0114f15788b7170be413a35a ;;
    *) echo "✗ no musl JetBrains JDK recorded for $(uname -m)" >&2; return 1 ;;
  esac
  n="jbrsdk-$JBR_V-linux-musl-$a-$JBR_B"
  jd="${XDG_CACHE_HOME:-$HOME/.cache}/build-apk/jdk"
  if [ ! -x "$jd/$n/bin/java" ]; then
    mkdir -p "$jd"
    echo "Downloading JetBrains JDK $JBR_V for musl/$a (~240 MB, once) for the Gradle daemon…" >&2
    dl "https://cache-redirector.jetbrains.com/intellij-jbr/$n.tar.gz" "$jd/$n.tar.gz" || { echo "✗ could not download $n" >&2; return 1; }
    [ "$(sha512sum "$jd/$n.tar.gz" | cut -d' ' -f1)" = "$sum" ] || { rm -f "$jd/$n.tar.gz"; echo "✗ $n: SHA-512 mismatch, file removed" >&2; return 1; }
    tar xzf "$jd/$n.tar.gz" -C "$jd"
    rm -f "$jd/$n.tar.gz"
  fi
  echo "$jd/$n"
}

cd "$ROOT/AndorsTrail"
if is_musl; then
  j=$(jbr_musl)
  sh ./gradlew --console=plain "-Dorg.gradle.java.installations.paths=$j" :app:assembleDebug
else
  sh ./gradlew --console=plain :app:assembleDebug
fi
ls "$ROOT"/AndorsTrail/app/build/outputs/apk/debug/*.apk
