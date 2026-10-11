#!/bin/bash
# MiFod Bridge 编译脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
PLATFORM="$ANDROID_HOME/platforms/android-35/android.jar"
D8="$ANDROID_HOME/build-tools/35.0.0/d8"

# 在 /tmp 编译避免 WSL NTFS 权限问题
BUILD_DIR=$(mktemp -d)
trap "rm -rf $BUILD_DIR" EXIT

cd "$SCRIPT_DIR"

# 检查依赖
if [ ! -f "$PLATFORM" ]; then
    echo "ERROR: android.jar not found: $PLATFORM"
    exit 1
fi
if [ ! -f "$D8" ]; then
    echo "ERROR: d8 not found: $D8"
    exit 1
fi

echo "=== 编译 MiFodBridge.java ==="
javac -source 11 -target 11 -classpath "$PLATFORM" -d "$BUILD_DIR" MiFodBridge.java

echo "=== d8 → dex ==="
"$D8" --lib "$PLATFORM" --output "$BUILD_DIR" \
    "$BUILD_DIR"/*.class

echo "=== 打包 fod-bridge.jar ==="
cd "$BUILD_DIR"
jar cf fod-bridge.jar classes.dex
cd "$SCRIPT_DIR"

# 复制到源码目录（用 cat 避免 WSL 权限问题）
cat "$BUILD_DIR/fod-bridge.jar" > fod-bridge.jar

SIZE=$(wc -c < fod-bridge.jar)
echo "=== 完成: fod-bridge.jar ($SIZE bytes) ==="
