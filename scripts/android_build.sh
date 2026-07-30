#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
ANDROID_DIR="$ROOT/implementation/android-native"
IMAGE_NAME="${ANDROID_BUILD_IMAGE:-helpify-mydealer-android:0.8.2}"
OUTPUT_DIR="${ANDROID_OUTPUT_DIR:-/var/lib/helpify-mydealer/android-builds/v0.13.0}"
CACHE_DIR="${ANDROID_CACHE_DIR:-/var/cache/helpify-mydealer/android}"

log(){ printf '[%(%H:%M:%S)T] %s\n' -1 "$*"; }
die(){ printf 'ERROR: %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "run as root"
command -v docker >/dev/null || die "Docker unavailable"

mkdir -p "$OUTPUT_DIR" "$CACHE_DIR/gradle" "$CACHE_DIR/project"
rm -f "$OUTPUT_DIR"/*.apk "$OUTPUT_DIR"/*.txt "$OUTPUT_DIR"/SHA256SUMS.txt "$OUTPUT_DIR"/build.env

docker image inspect "$IMAGE_NAME" >/dev/null 2>&1 || \
  docker build --pull -t "$IMAGE_NAME" -f "$ROOT/infrastructure/android/Dockerfile" "$ROOT"

log "building Android apps sequentially"
docker run --rm \
  -v "$ANDROID_DIR:/source:ro" \
  -v "$OUTPUT_DIR:/out" \
  -v "$CACHE_DIR/gradle:/cache/gradle" \
  -v "$CACHE_DIR/project:/cache/project" \
  -e GRADLE_USER_HOME=/cache/gradle \
  "$IMAGE_NAME" \
  bash -Eeuo pipefail -c '
    rm -rf /workspace/android
    mkdir -p /workspace/android
    cp -a /source/. /workspace/android/
    cd /workspace/android

    args=(
      --no-daemon
      --no-parallel
      --max-workers=1
      --stacktrace
      --project-cache-dir /cache/project
    )

    gradle "${args[@]}" :helpify:assembleDebug
    gradle "${args[@]}" :mydealer:assembleDebug

    install -m 0644 helpify/build/outputs/apk/debug/helpify-debug.apk /out/Helpify-v0.13.0-debug.apk
    install -m 0644 mydealer/build/outputs/apk/debug/mydealer-debug.apk /out/MyDealer-v0.13.0-debug.apk

    aapt dump badging /out/Helpify-v0.13.0-debug.apk > /out/Helpify-v0.13.0-badging.txt
    aapt dump badging /out/MyDealer-v0.13.0-debug.apk > /out/MyDealer-v0.13.0-badging.txt

    cd /out
    sha256sum Helpify-v0.13.0-debug.apk MyDealer-v0.13.0-debug.apk > SHA256SUMS.txt
  '

cat > "$OUTPUT_DIR/build.env" <<META
release=0.13.0
image=$IMAGE_NAME
built_at_utc=$(date -u +%FT%TZ)
gradle_heap_mb=1024
gradle_workers=1
build_mode=sequential
META

"$ROOT/scripts/android_build_doctor.sh" artifacts
ls -lh "$OUTPUT_DIR"
