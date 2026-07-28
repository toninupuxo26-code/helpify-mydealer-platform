#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
ANDROID_DIR="$ROOT/implementation/android-native"
IMAGE_NAME="${ANDROID_BUILD_IMAGE:-helpify-mydealer-android:0.8.2}"
OUTPUT_DIR="${ANDROID_OUTPUT_DIR:-/var/lib/helpify-mydealer/android-builds/v0.8.2}"
CACHE_DIR="${ANDROID_CACHE_DIR:-/var/cache/helpify-mydealer/android}"
BUILD_IMAGE="${ANDROID_REBUILD_IMAGE:-0}"

log(){ printf '[%(%H:%M:%S)T] %s\n' -1 "$*"; }
die(){ printf 'ERROR: %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "run as root or with sudo"
command -v docker >/dev/null || die "Docker is unavailable"
[[ "$(uname -m)" == "x86_64" ]] || die "Android command-line tools require an x86_64 build host"
[[ -f "$ANDROID_DIR/settings.gradle" ]] || die "Android workspace is missing"

mkdir -p "$OUTPUT_DIR" "$CACHE_DIR/gradle" "$CACHE_DIR/project"
rm -f "$OUTPUT_DIR"/*.apk "$OUTPUT_DIR"/*.txt "$OUTPUT_DIR"/*.sha256 "$OUTPUT_DIR"/build.env

if [[ "$BUILD_IMAGE" == "1" ]] || ! docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
  log "building Android toolchain image"
  docker build \
    --pull \
    --tag "$IMAGE_NAME" \
    --file "$ROOT/infrastructure/android/Dockerfile" \
    "$ROOT"
else
  log "using existing Android toolchain image: $IMAGE_NAME"
fi

log "building Helpify and MyDealer debug APKs"
docker run --rm \
  --volume "$ANDROID_DIR:/source:ro" \
  --volume "$OUTPUT_DIR:/out" \
  --volume "$CACHE_DIR/gradle:/cache/gradle" \
  --volume "$CACHE_DIR/project:/cache/project" \
  --env GRADLE_USER_HOME=/cache/gradle \
  "$IMAGE_NAME" \
  bash -Eeuo pipefail -c '
    rm -rf /workspace/android
    mkdir -p /workspace/android
    cp -a /source/. /workspace/android/
    cd /workspace/android

    gradle \
      --no-daemon \
      --stacktrace \
      --project-cache-dir /cache/project \
      :helpify:assembleDebug \
      :mydealer:assembleDebug

    install -m 0644 \
      helpify/build/outputs/apk/debug/helpify-debug.apk \
      /out/Helpify-v0.8.2-debug.apk

    install -m 0644 \
      mydealer/build/outputs/apk/debug/mydealer-debug.apk \
      /out/MyDealer-v0.8.2-debug.apk

    aapt dump badging /out/Helpify-v0.8.2-debug.apk \
      > /out/Helpify-v0.8.2-badging.txt

    aapt dump badging /out/MyDealer-v0.8.2-debug.apk \
      > /out/MyDealer-v0.8.2-badging.txt

    cd /out
    sha256sum \
      Helpify-v0.8.2-debug.apk \
      MyDealer-v0.8.2-debug.apk \
      > SHA256SUMS.txt
  '

cat > "$OUTPUT_DIR/build.env" <<EOF
release=0.8.2
image=$IMAGE_NAME
built_at_utc=$(date -u +%FT%TZ)
helpify_apk=Helpify-v0.8.2-debug.apk
mydealer_apk=MyDealer-v0.8.2-debug.apk
EOF

log "verifying artifacts"
"$ROOT/scripts/android_build_doctor.sh" artifacts

printf '\nAndroid artifacts:\n'
ls -lh "$OUTPUT_DIR"
