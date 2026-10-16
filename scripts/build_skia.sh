#!/usr/bin/env bash
# Builds Skia (Ganesh/GL) static libs for Android and installs them where CMakeLists.txt expects them.
# Needs: git, python3, ninja, ~10 GB disk, 30–60 min. Network required (once).
set -euo pipefail

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to your NDK r27 directory}"
SKIA_REF="${SKIA_REF:-chrome/m126}"   # the native code targets this Skia milestone's API
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/cpp/third_party/skia"

if [ ! -d "$DEST/.git" ]; then
  git clone https://skia.googlesource.com/skia.git "$DEST"
fi
cd "$DEST"
git fetch origin "$SKIA_REF"
git checkout FETCH_HEAD
python3 tools/git-sync-deps

build_abi() {
  local abi="$1" cpu="$2"
  bin/gn gen "out/$abi" --args="
    ndk=\"$ANDROID_NDK_HOME\" ndk_api=34
    target_os=\"android\" target_cpu=\"$cpu\"
    is_official_build=true is_component_build=false
    skia_enable_ganesh=true skia_use_gl=true skia_use_vulkan=false
    skia_enable_graphite=false skia_enable_tools=false
        skia_enable_pdf=false skia_enable_skottie=false
    skia_use_system_freetype2=false skia_use_system_expat=false
    skia_use_system_harfbuzz=false skia_use_system_icu=false
    skia_use_system_libjpeg_turbo=false skia_use_system_libpng=false
    skia_use_system_libwebp=false skia_use_system_zlib=false"
  ninja -C "out/$abi" skia
  mkdir -p "lib/$abi"
  cp "out/$abi"/*.a "lib/$abi/"     # libskia.a plus bundled third-party archives (freetype, etc.)
}

build_abi arm64-v8a arm64
build_abi x86_64 x64
echo "Skia installed under $DEST/lib. Rebuild the app to get the Skia renderer."
