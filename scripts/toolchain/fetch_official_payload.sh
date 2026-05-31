#!/usr/bin/env bash
set -euo pipefail

# Fetches the official, traceable subset of the Android offline toolchain payload.
# NOTE: This script intentionally does NOT fetch aapt2-arm64.
#       JRE is optional for Java-only builds because d8/apksigner have dalvikvm fallback.

DEST_DIR="app/src/main/assets/toolchains/android/arm64-v8a"
ANDROID_API="34"
R8_VERSION="9.0.32"
KOTLIN_VERSION="2.0.21"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dest)
      DEST_DIR="$2"
      shift 2
      ;;
    --android-api)
      ANDROID_API="$2"
      shift 2
      ;;
    --r8-version)
      R8_VERSION="$2"
      shift 2
      ;;
    --kotlin-version)
      KOTLIN_VERSION="$2"
      shift 2
      ;;
    *)
      echo "[FAIL] Unknown argument: $1"
      exit 1
      ;;
  esac
done

mkdir -p "$DEST_DIR/lib" "$DEST_DIR/lib64"

fetch_gitiles_blob() {
  local url="$1"
  local out="$2"
  curl -fsSL "$url" | base64 -d > "$out"
}

echo "[INFO] Destination: $DEST_DIR"
echo "[INFO] Android API: $ANDROID_API"
echo "[INFO] R8 version: $R8_VERSION"
echo "[INFO] Kotlin version: $KOTLIN_VERSION"

echo "[INFO] Fetching zipalign + runtime libs (AOSP prebuilts/build-tools linux-arm64)..."
fetch_gitiles_blob \
  "https://android.googlesource.com/platform/prebuilts/build-tools/+/refs/heads/main/linux-arm64/bin/zipalign?format=TEXT" \
  "$DEST_DIR/zipalign"
fetch_gitiles_blob \
  "https://android.googlesource.com/platform/prebuilts/build-tools/+/refs/heads/main/linux-arm64/lib64/libc++.so?format=TEXT" \
  "$DEST_DIR/lib64/libc++.so"
fetch_gitiles_blob \
  "https://android.googlesource.com/platform/prebuilts/build-tools/+/refs/heads/main/linux-arm64/lib64/libc_musl.so?format=TEXT" \
  "$DEST_DIR/lib64/libc_musl.so"

echo "[INFO] Fetching android.jar (AOSP prebuilts/sdk)..."
fetch_gitiles_blob \
  "https://android.googlesource.com/platform/prebuilts/sdk/+/refs/heads/main/${ANDROID_API}/public/android.jar?format=TEXT" \
  "$DEST_DIR/android.jar"

echo "[INFO] Fetching r8.jar (Google Maven)..."
curl -fsSL \
  "https://dl.google.com/dl/android/maven2/com/android/tools/r8/${R8_VERSION}/r8-${R8_VERSION}.jar" \
  -o "$DEST_DIR/lib/r8.jar"

echo "[INFO] Fetching apksigner.jar (AOSP prebuilts/fullsdk-linux build-tools/30.0.3)..."
fetch_gitiles_blob \
  "https://android.googlesource.com/platform/prebuilts/fullsdk-linux/build-tools/+/refs/heads/main/30.0.3/lib/apksigner.jar?format=TEXT" \
  "$DEST_DIR/lib/apksigner.jar"

echo "[INFO] Fetching Kotlin compiler (Maven Central)..."
curl -fsSL \
  "https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/${KOTLIN_VERSION}/kotlin-compiler-embeddable-${KOTLIN_VERSION}.jar" \
  -o "$DEST_DIR/lib/kotlin-compiler-embeddable.jar"

if ! command -v java >/dev/null 2>&1; then
  echo "[FAIL] java command not found on host (required to generate dex fallback jars)"
  exit 1
fi

echo "[INFO] Generating ART dex fallback jars for d8/apksigner..."
java -cp "$DEST_DIR/lib/r8.jar" com.android.tools.r8.D8 --min-api 26 \
  --output "$DEST_DIR/lib/r8-dex.jar" "$DEST_DIR/lib/r8.jar"
java -cp "$DEST_DIR/lib/r8.jar" com.android.tools.r8.D8 --min-api 26 \
  --output "$DEST_DIR/lib/apksigner-dex.jar" "$DEST_DIR/lib/apksigner.jar"

echo "[INFO] Writing wrapper scripts..."
cat > "$DEST_DIR/java" <<'EOF'
#!/system/bin/sh
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ -x "$DIR/jre/bin/java" ]; then
  exec "$DIR/jre/bin/java" "$@"
fi
if [ -x /system/bin/java ]; then
  exec /system/bin/java "$@"
fi
echo "No Java runtime found. Bundle toolchain jre/ or skip Kotlin compilation." >&2
exit 127
EOF

cat > "$DEST_DIR/d8" <<'EOF'
#!/system/bin/sh
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ -x "$DIR/jre/bin/java" ]; then
  exec "$DIR/jre/bin/java" -cp "$DIR/lib/r8.jar" com.android.tools.r8.D8 "$@"
fi
ART_VM="/apex/com.android.art/bin/dalvikvm"
if [ ! -x "$ART_VM" ]; then
  ART_VM="/system/bin/dalvikvm"
fi
if [ -x "$ART_VM" ] && [ -f "$DIR/lib/r8-dex.jar" ]; then
  if [ -w "$DIR/lib/r8-dex.jar" ]; then
    chmod 444 "$DIR/lib/r8-dex.jar" 2>/dev/null || true
  fi
  exec "$ART_VM" -Xmx1024m -cp "$DIR/lib/r8-dex.jar" com.android.tools.r8.D8 "$@"
fi
echo "No available runtime for d8 (missing bundled jre and ART dex fallback)." >&2
exit 127
EOF

cat > "$DEST_DIR/apksigner" <<'EOF'
#!/system/bin/sh
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ -x "$DIR/jre/bin/java" ]; then
  exec "$DIR/jre/bin/java" -jar "$DIR/lib/apksigner.jar" "$@"
fi
ART_VM="/apex/com.android.art/bin/dalvikvm"
if [ ! -x "$ART_VM" ]; then
  ART_VM="/system/bin/dalvikvm"
fi
if [ -x "$ART_VM" ] && [ -f "$DIR/lib/apksigner-dex.jar" ]; then
  if [ -w "$DIR/lib/apksigner-dex.jar" ]; then
    chmod 444 "$DIR/lib/apksigner-dex.jar" 2>/dev/null || true
  fi
  exec "$ART_VM" -cp "$DIR/lib/apksigner-dex.jar" com.android.apksigner.ApkSignerTool "$@"
fi
echo "No available runtime for apksigner (missing bundled jre and ART dex fallback)." >&2
exit 127
EOF

cat > "$DEST_DIR/kotlinc" <<'EOF'
#!/system/bin/sh
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec "$DIR/java" -cp "$DIR/lib/kotlin-compiler-embeddable.jar" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "$@"
EOF

chmod +x \
  "$DEST_DIR/zipalign" \
  "$DEST_DIR/java" \
  "$DEST_DIR/d8" \
  "$DEST_DIR/apksigner" \
  "$DEST_DIR/kotlinc"

cat > "$DEST_DIR/metadata.auto.json" <<EOF
{
  "aapt2": {
    "name": "aapt2",
    "sourceUrl": "manual-provided",
    "versionOrCommit": "manual",
    "license": "Apache-2.0",
    "executable": true
  },
  "zipalign": {
    "name": "zipalign",
    "sourceUrl": "https://android.googlesource.com/platform/prebuilts/build-tools/+/refs/heads/main/linux-arm64/bin/zipalign",
    "versionOrCommit": "refs/heads/main",
    "license": "Apache-2.0",
    "executable": true
  },
  "lib64/libc++.so": {
    "name": "libc++.so",
    "sourceUrl": "https://android.googlesource.com/platform/prebuilts/build-tools/+/refs/heads/main/linux-arm64/lib64/libc%2B%2B.so",
    "versionOrCommit": "refs/heads/main",
    "license": "Apache-2.0"
  },
  "lib64/libc_musl.so": {
    "name": "libc_musl.so",
    "sourceUrl": "https://android.googlesource.com/platform/prebuilts/build-tools/+/refs/heads/main/linux-arm64/lib64/libc_musl.so",
    "versionOrCommit": "refs/heads/main",
    "license": "MIT"
  },
  "android.jar": {
    "name": "android.jar",
    "sourceUrl": "https://android.googlesource.com/platform/prebuilts/sdk/+/refs/heads/main/${ANDROID_API}/public/android.jar",
    "versionOrCommit": "refs/heads/main",
    "license": "Apache-2.0"
  },
  "lib/r8.jar": {
    "name": "r8.jar",
    "sourceUrl": "https://dl.google.com/dl/android/maven2/com/android/tools/r8/${R8_VERSION}/r8-${R8_VERSION}.jar",
    "versionOrCommit": "${R8_VERSION}",
    "license": "BSD-3-Clause"
  },
  "lib/r8-dex.jar": {
    "name": "r8-dex.jar",
    "sourceUrl": "derived-from:lib/r8.jar",
    "versionOrCommit": "generated-with-d8-min-api-26",
    "license": "BSD-3-Clause"
  },
  "lib/apksigner.jar": {
    "name": "apksigner.jar",
    "sourceUrl": "https://android.googlesource.com/platform/prebuilts/fullsdk-linux/build-tools/+/refs/heads/main/30.0.3/lib/apksigner.jar",
    "versionOrCommit": "30.0.3",
    "license": "Apache-2.0"
  },
  "lib/apksigner-dex.jar": {
    "name": "apksigner-dex.jar",
    "sourceUrl": "derived-from:lib/apksigner.jar",
    "versionOrCommit": "generated-with-d8-min-api-26",
    "license": "Apache-2.0"
  },
  "lib/kotlin-compiler-embeddable.jar": {
    "name": "kotlin-compiler-embeddable.jar",
    "sourceUrl": "https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/${KOTLIN_VERSION}/kotlin-compiler-embeddable-${KOTLIN_VERSION}.jar",
    "versionOrCommit": "${KOTLIN_VERSION}",
    "license": "Apache-2.0"
  },
  "java": {
    "name": "java",
    "sourceUrl": "local-wrapper",
    "versionOrCommit": "v2-system-fallback",
    "license": "Proprietary (OmniMaster)",
    "executable": true
  },
  "d8": {
    "name": "d8",
    "sourceUrl": "local-wrapper",
    "versionOrCommit": "v2-dalvik-fallback",
    "license": "Proprietary (OmniMaster)",
    "executable": true
  },
  "apksigner": {
    "name": "apksigner",
    "sourceUrl": "local-wrapper",
    "versionOrCommit": "v2-dalvik-fallback",
    "license": "Proprietary (OmniMaster)",
    "executable": true
  },
  "kotlinc": {
    "name": "kotlinc",
    "sourceUrl": "local-wrapper",
    "versionOrCommit": "v1",
    "license": "Proprietary (OmniMaster)",
    "executable": true
  }
}
EOF

echo "[INFO] Downloaded files:"
ls -lh "$DEST_DIR" "$DEST_DIR/lib" "$DEST_DIR/lib64"

echo
echo "[NEXT] You still must provide:"
echo "  1) $DEST_DIR/aapt2    (Android ARM64 runnable binary)"
echo "  2) (optional) $DEST_DIR/jre/... for Kotlin offline compilation (d8/apksigner can use dalvikvm fallback)"
echo "  3) (recommended) replace metadata.auto.json aapt2 source/version placeholders"
echo
echo "[NEXT] Then run:"
echo "  python scripts/toolchain/build_manifest.py generate --abi-dir \"$DEST_DIR\" --version v1 --metadata \"$DEST_DIR/metadata.auto.json\""
echo "  python scripts/toolchain/build_manifest.py validate --abi-dir \"$DEST_DIR\" --required aapt2,d8,zipalign,apksigner,android.jar,java,kotlinc"
