#!/bin/bash
# 万灵驭手 · 实时演示启动脚本
# 功能：1) 启动 scrcpy 到 v4l2loopback  2) 启动本地 HTTP 服务器

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PPT_FILE="万灵驭手_展示汇报.html"
PORT=8080

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "========================================"
echo "  万灵驭手 OmniMaster · 演示启动器"
echo "========================================"
echo ""

# 检查 scrcpy
if ! command -v scrcpy &> /dev/null; then
    echo -e "${RED}✗ scrcpy 未安装${NC}"
    echo "  请运行: sudo pacman -S scrcpy"
    exit 1
fi
echo -e "${GREEN}✓ scrcpy 已安装${NC}"

# 检查 adb 设备
echo ""
echo "[1/4] 检查手机连接..."
adb_devices=$(adb devices -l 2>/dev/null | grep -v "List of devices" | grep -v "^$")
if [ -z "$adb_devices" ]; then
    echo -e "${RED}✗ 未检测到手机${NC}"
    echo ""
    echo "请按以下步骤排查:"
    echo "  1. 手机设置 → 关于手机 → 连续点击'版本号'7次，开启开发者模式"
    echo "  2. 设置 → 系统 → 开发者选项 → 开启'USB调试'"
    echo "  3. 用数据线连接电脑，手机上弹出'允许USB调试?' → 点击'允许'"
    echo "  4. 下拉通知栏，把USB用途从'仅充电'改为'文件传输'"
    echo "  5. 重新运行本脚本"
    echo ""
    echo "如果已确认以上步骤，尝试:"
    echo "  adb kill-server && adb start-server"
    exit 1
fi
echo -e "${GREEN}✓ 手机已连接${NC}"
echo "  $adb_devices"

# 检查 v4l2loopback
echo ""
echo "[2/4] 检查 v4l2loopback 模块..."
if ! lsmod | grep -q v4l2loopback; then
    echo -e "${YELLOW}! v4l2loopback 未加载，正在加载...${NC}"
    sudo modprobe v4l2loopback 2>/dev/null || {
        echo -e "${RED}✗ 加载失败${NC}"
        echo "  请运行: sudo pacman -S v4l2loopback-dkms linux-headers"
        echo "  然后:   sudo modprobe v4l2loopback"
        exit 1
    }
fi
echo -e "${GREEN}✓ v4l2loopback 已加载${NC}"

# 查找 v4l2loopback 设备
echo ""
echo "[3/4] 查找虚拟摄像头设备..."
V4L2_DEV=""
for dev in /dev/video*; do
    if v4l2-ctl -d "$dev" --all 2>/dev/null | grep -q "Driver name\s*:\s*v4l2 loopback"; then
        V4L2_DEV="$dev"
        break
    fi
done

if [ -z "$V4L2_DEV" ]; then
    # fallback: 找最新的 video 设备
    V4L2_DEV=$(ls /dev/video* | sort -V | tail -1)
    echo -e "${YELLOW}! 未明确识别 v4l2loopback，使用最新设备: $V4L2_DEV${NC}"
else
    echo -e "${GREEN}✓ 虚拟摄像头: $V4L2_DEV${NC}"
fi

# 启动 scrcpy
echo ""
echo "[4/4] 启动 scrcpy → v4l2loopback..."
echo -e "${GREEN}✓ 正在启动 scrcpy（后台运行）${NC}"
scrcpy \
    --v4l2-sink="$V4L2_DEV" \
    --no-playback \
    --stay-awake \
    --show-touches &
SCRCPY_PID=$!

sleep 2
if ! kill -0 $SCRCPY_PID 2>/dev/null; then
    echo -e "${RED}✗ scrcpy 启动失败${NC}"
    echo "  尝试不带 --no-display 启动以查看错误:"
    echo "  scrcpy --v4l2-sink=$V4L2_DEV"
    exit 1
fi

echo -e "${GREEN}✓ scrcpy PID: $SCRCPY_PID${NC}"

# 启动 HTTP 服务器
echo ""
echo "========================================"
echo "  启动 HTTP 服务器..."
echo "========================================"
cd "$PROJECT_DIR"
echo ""
echo -e "${GREEN}PPT 地址: http://localhost:$PORT/$PPT_FILE${NC}"
echo ""
echo "操作步骤:"
echo "  1. 用浏览器打开上面的地址"
echo "  2. 按 F11 全屏"
echo "  3. 方向键翻页到第 5 页（实时演示）"
echo "  4. 浏览器会请求摄像头权限 → 点击'允许'"
echo ""
echo "停止演示:"
echo "  Ctrl+C 停止服务器，然后: kill $SCRCPY_PID"
echo ""

# 用 python 启动服务器
if command -v python3 &> /dev/null; then
    python3 -m http.server "$PORT"
elif command -v python &> /dev/null; then
    python -m http.server "$PORT"
else
    echo -e "${RED}✗ 未找到 python3/python${NC}"
    echo "  请安装: sudo pacman -S python"
    exit 1
fi
