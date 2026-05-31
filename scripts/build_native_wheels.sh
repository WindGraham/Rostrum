#!/bin/bash
#
# build_native_wheels.sh - 使用 Android NDK 交叉编译 Python 包为 Android wheel
#
# 此脚本用于为 OmniMaster 的嵌入式 Python 3.14 环境构建含 C 扩展的 Python 包。
# 构建结果（.whl 文件）放入 app/src/main/assets/python_packages/ 目录。
#
# 前置条件：
#   1. Android NDK r26+ (设置 ANDROID_NDK_HOME 环境变量)
#   2. Python 3.14 cpython 源码交叉编译产物（包括 include 和 lib）
#   3. crossenv 或 python-for-android 交叉编译环境
#
# 使用方法：
#   # 编译所有已知包
#   ./scripts/build_native_wheels.sh all
#
#   # 编译指定包
#   ./scripts/build_native_wheels.sh pillow numpy
#
#   # 仅下载已有的预编译 wheel（从 Termux / pypi 等源）
#   ./scripts/build_native_wheels.sh --download-only pillow
#
# 输出目录：
#   app/src/main/assets/python_packages/
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/app/src/main/assets/python_packages"

# Android NDK 配置
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/26.1.10909125}"
ANDROID_API=24
ANDROID_ARCH=aarch64
ANDROID_ABI=arm64-v8a
TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"

# Python 配置（必须与 OmniMaster 中的 Python 版本匹配）
PYTHON_VERSION="3.14"
PYTHON_ABI_TAG="cp314"
PLATFORM_TAG="linux_aarch64"

# 交叉编译宿主 Python 路径（用于运行 setup.py）
HOST_PYTHON="${HOST_PYTHON:-python3.14}"

# 交叉编译产物路径（Python 头文件和库）
# 需要先交叉编译 CPython 3.14 for aarch64-linux-android
CROSS_PYTHON_PREFIX="${CROSS_PYTHON_PREFIX:-$PROJECT_ROOT/build-python-android}"

# 编译工作目录
BUILD_DIR="$PROJECT_ROOT/build/native-wheels"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ============================================================================
# 环境检查
# ============================================================================
check_environment() {
    log_info "检查构建环境..."
    
    # 检查 NDK
    if [ ! -d "$ANDROID_NDK_HOME" ]; then
        log_error "Android NDK 未找到: $ANDROID_NDK_HOME"
        log_error "请设置 ANDROID_NDK_HOME 环境变量"
        exit 1
    fi
    log_ok "NDK: $ANDROID_NDK_HOME"
    
    # 检查交叉编译工具链
    local CC="$TOOLCHAIN/bin/${ANDROID_ARCH}-linux-android${ANDROID_API}-clang"
    if [ ! -f "$CC" ]; then
        log_error "交叉编译器未找到: $CC"
        exit 1
    fi
    log_ok "CC: $CC"
    
    # 检查宿主 Python
    if ! command -v "$HOST_PYTHON" &>/dev/null; then
        log_warn "宿主 Python ($HOST_PYTHON) 未找到，尝试 python3..."
        HOST_PYTHON="python3"
        if ! command -v "$HOST_PYTHON" &>/dev/null; then
            log_error "需要宿主 Python 来运行构建脚本"
            exit 1
        fi
    fi
    log_ok "Host Python: $($HOST_PYTHON --version)"
    
    # 创建输出目录
    mkdir -p "$OUTPUT_DIR"
    mkdir -p "$BUILD_DIR"
    
    log_ok "环境检查通过"
}

# ============================================================================
# 设置交叉编译环境变量
# ============================================================================
setup_cross_env() {
    export CC="$TOOLCHAIN/bin/${ANDROID_ARCH}-linux-android${ANDROID_API}-clang"
    export CXX="$TOOLCHAIN/bin/${ANDROID_ARCH}-linux-android${ANDROID_API}-clang++"
    export AR="$TOOLCHAIN/bin/llvm-ar"
    export AS="$CC"
    export LD="$TOOLCHAIN/bin/ld"
    export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
    export STRIP="$TOOLCHAIN/bin/llvm-strip"
    export NM="$TOOLCHAIN/bin/llvm-nm"
    export READELF="$TOOLCHAIN/bin/llvm-readelf"
    
    # Python 交叉编译头文件和库路径
    if [ -d "$CROSS_PYTHON_PREFIX" ]; then
        export CFLAGS="-I$CROSS_PYTHON_PREFIX/include/python${PYTHON_VERSION} -fPIC -O2"
        export LDFLAGS="-L$CROSS_PYTHON_PREFIX/lib -lpython${PYTHON_VERSION}"
        export PKG_CONFIG_PATH="$CROSS_PYTHON_PREFIX/lib/pkgconfig"
    else
        export CFLAGS="-fPIC -O2"
        export LDFLAGS=""
    fi
    
    export CXXFLAGS="$CFLAGS"
    
    # 确保 pip wheel 使用正确的平台标签
    export _PYTHON_HOST_PLATFORM="linux-aarch64"
    export ARCHFLAGS="-arch arm64"
}

# ============================================================================
# 使用 crossenv 构建包
# ============================================================================
build_with_crossenv() {
    local package_name="$1"
    local package_spec="${2:-$package_name}"
    
    log_info "构建 $package_name ..."
    
    local pkg_build_dir="$BUILD_DIR/$package_name"
    mkdir -p "$pkg_build_dir"
    cd "$pkg_build_dir"
    
    setup_cross_env
    
    # 使用 pip wheel 构建
    $HOST_PYTHON -m pip wheel \
        --no-deps \
        --no-build-isolation \
        --wheel-dir="$pkg_build_dir/dist" \
        "$package_spec" \
        2>&1 | tee "$pkg_build_dir/build.log"
    
    # 复制结果
    local wheel_files=(dist/*.whl)
    if [ ${#wheel_files[@]} -gt 0 ] && [ -f "${wheel_files[0]}" ]; then
        for whl in dist/*.whl; do
            local new_name
            new_name=$(rename_wheel_platform "$whl")
            cp "$whl" "$OUTPUT_DIR/$new_name"
            log_ok "$package_name: $new_name -> $OUTPUT_DIR/"
        done
    else
        log_error "$package_name: 构建失败，未生成 wheel 文件"
        return 1
    fi
}

# ============================================================================
# 重命名 wheel 平台标签
# ============================================================================
rename_wheel_platform() {
    local whl_path="$1"
    local filename
    filename=$(basename "$whl_path")
    
    # 如果已经有正确的平台标签，直接返回
    if echo "$filename" | grep -q "${PLATFORM_TAG}"; then
        echo "$filename"
        return
    fi
    
    # 替换平台标签
    # 格式: {name}-{ver}-{python}-{abi}-{platform}.whl
    echo "$filename" | sed -E "s/[^-]+-[^-]+\.whl/${PYTHON_ABI_TAG}-${PLATFORM_TAG}.whl/"
}

# ============================================================================
# 从已知源下载预编译 wheel
# ============================================================================
download_prebuilt() {
    local package_name="$1"
    
    log_info "搜索 $package_name 的预编译 wheel..."
    
    # 源1: PyPI（可能有 manylinux aarch64 wheel）
    log_info "检查 PyPI..."
    $HOST_PYTHON -m pip download \
        --no-deps \
        --only-binary=:all: \
        --platform manylinux2014_aarch64 \
        --python-version "${PYTHON_VERSION}" \
        --abi "${PYTHON_ABI_TAG}" \
        --dest "$BUILD_DIR/downloads" \
        "$package_name" 2>/dev/null && {
        local whl_files=("$BUILD_DIR/downloads"/*.whl)
        if [ ${#whl_files[@]} -gt 0 ] && [ -f "${whl_files[0]}" ]; then
            for whl in "$BUILD_DIR/downloads"/*.whl; do
                cp "$whl" "$OUTPUT_DIR/"
                log_ok "从 PyPI 下载: $(basename "$whl")"
            done
            return 0
        fi
    }
    
    log_warn "$package_name: PyPI 上未找到兼容的预编译 wheel"
    return 1
}

# ============================================================================
# 已知包的特殊构建配置
# ============================================================================
build_pillow() {
    log_info "构建 Pillow（需要 libjpeg, libpng, zlib）..."
    
    setup_cross_env
    
    # Pillow 依赖的原生库
    # 需要先交叉编译 libjpeg-turbo, libpng, zlib
    local deps_dir="$BUILD_DIR/pillow-deps"
    mkdir -p "$deps_dir"
    
    # zlib 通常包含在 NDK sysroot 中
    local SYSROOT="$TOOLCHAIN/sysroot"
    
    export CFLAGS="$CFLAGS -I$SYSROOT/usr/include"
    export LDFLAGS="$LDFLAGS -L$SYSROOT/usr/lib/${ANDROID_ARCH}-linux-android/${ANDROID_API}"
    
    # 禁用不必要的图片格式，只保留基础支持
    export MAX_CONCURRENCY=4
    
    build_with_crossenv "Pillow" "Pillow"
}

build_numpy() {
    log_info "构建 NumPy..."
    
    setup_cross_env
    
    # NumPy 使用 meson 构建系统
    # 需要 meson-python, cython, numpy 特定配置
    
    local numpy_build_dir="$BUILD_DIR/numpy"
    mkdir -p "$numpy_build_dir"
    
    # 创建 meson 交叉编译配置文件
    cat > "$numpy_build_dir/android-cross.ini" << EOF
[binaries]
c = '$CC'
cpp = '$CXX'
ar = '$AR'
strip = '$STRIP'

[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'

[properties]
needs_exe_wrapper = true
EOF
    
    build_with_crossenv "numpy" "numpy"
}

# ============================================================================
# 主入口
# ============================================================================
usage() {
    echo "用法: $0 [选项] [包名...]"
    echo ""
    echo "选项:"
    echo "  all              构建所有已知包"
    echo "  --download-only  仅下载预编译 wheel（不编译）"
    echo "  --list           列出所有支持的包"
    echo "  --clean          清理构建目录"
    echo "  -h, --help       显示帮助"
    echo ""
    echo "示例:"
    echo "  $0 pillow numpy     # 构建 Pillow 和 NumPy"
    echo "  $0 all              # 构建所有已知包"
    echo "  $0 --download-only pillow  # 仅下载"
    echo ""
    echo "环境变量:"
    echo "  ANDROID_NDK_HOME     Android NDK 路径"
    echo "  HOST_PYTHON          宿主 Python 可执行文件"
    echo "  CROSS_PYTHON_PREFIX  交叉编译 Python 安装前缀"
}

KNOWN_PACKAGES=(
    "Pillow"
    "numpy"
    "scipy"
    "pandas"
    "matplotlib"
    "lxml"
    "cryptography"
    "cffi"
    "psutil"
    "ujson"
    "msgpack"
    "orjson"
    "PyYAML"
    "MarkupSafe"
    "regex"
    "aiohttp"
    "yarl"
    "multidict"
    "frozenlist"
    "charset-normalizer"
    "greenlet"
)

main() {
    if [ $# -eq 0 ] || [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
        usage
        exit 0
    fi
    
    case "$1" in
        --list)
            echo "支持的包:"
            for pkg in "${KNOWN_PACKAGES[@]}"; do
                echo "  - $pkg"
            done
            exit 0
            ;;
        --clean)
            log_info "清理构建目录..."
            rm -rf "$BUILD_DIR"
            log_ok "构建目录已清理"
            exit 0
            ;;
    esac
    
    check_environment
    
    local download_only=false
    local packages=()
    
    for arg in "$@"; do
        case "$arg" in
            --download-only) download_only=true ;;
            all)             packages=("${KNOWN_PACKAGES[@]}") ;;
            *)               packages+=("$arg") ;;
        esac
    done
    
    if [ ${#packages[@]} -eq 0 ]; then
        log_error "未指定要构建的包"
        usage
        exit 1
    fi
    
    local success=0
    local fail=0
    
    for pkg in "${packages[@]}"; do
        local pkg_lower
        pkg_lower=$(echo "$pkg" | tr '[:upper:]' '[:lower:]')
        
        if $download_only; then
            download_prebuilt "$pkg" && ((success++)) || ((fail++))
        else
            case "$pkg_lower" in
                pillow|pil)    build_pillow && ((success++)) || ((fail++)) ;;
                numpy)         build_numpy && ((success++)) || ((fail++)) ;;
                *)             build_with_crossenv "$pkg" "$pkg" && ((success++)) || ((fail++)) ;;
            esac
        fi
    done
    
    echo ""
    echo "============================================"
    log_info "构建完成: $success 成功, $fail 失败"
    echo "输出目录: $OUTPUT_DIR"
    echo ""
    
    if [ -d "$OUTPUT_DIR" ]; then
        local whl_count
        whl_count=$(find "$OUTPUT_DIR" -name "*.whl" 2>/dev/null | wc -l)
        log_info "assets/python_packages/ 中共有 $whl_count 个 wheel 文件:"
        find "$OUTPUT_DIR" -name "*.whl" -exec basename {} \; 2>/dev/null | sort
    fi
}

main "$@"
