#!/bin/bash
set -e

# Rostrum Server 安装脚本
# 用法: curl -fsSL https://your-domain.com/install.sh | bash

ROSTRUM_VERSION="1.0.0"
ROSTRUM_PORT="${ROSTRUM_PORT:-8080}"
ROSTRUM_HOST="${ROSTRUM_HOST:-127.0.0.1}"
ROSTRUM_WORKSPACE="${ROSTRUM_WORKSPACE:-/}"
ROSTRUM_INSTALL_DIR="${ROSTRUM_INSTALL_DIR:-$HOME/.rostrum}"
ROSTRUM_ALLOW_COMMAND="${ROSTRUM_ALLOW_COMMAND:-true}"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检测系统架构
detect_arch() {
    local arch=$(uname -m)
    case $arch in
        x86_64)
            echo "amd64"
            ;;
        aarch64)
            echo "arm64"
            ;;
        armv7l)
            echo "arm"
            ;;
        *)
            log_error "Unsupported architecture: $arch"
            exit 1
            ;;
    esac
}

# 检测操作系统
detect_os() {
    local os=$(uname -s)
    case $os in
        Linux)
            echo "linux"
            ;;
        Darwin)
            echo "darwin"
            ;;
        *)
            log_error "Unsupported OS: $os"
            exit 1
            ;;
    esac
}

# 检查依赖
check_dependencies() {
    log_info "Checking dependencies..."
    
    if ! command -v curl &> /dev/null; then
        log_error "curl is required but not installed"
        exit 1
    fi
    
    if ! command -v tar &> /dev/null; then
        log_error "tar is required but not installed"
        exit 1
    fi
}

# 下载 rostrum-server
download_server() {
    local arch=$(detect_arch)
    local os=$(detect_os)
    local filename="rostrum-server-${os}-${arch}.tar.gz"
    local download_url="https://github.com/WindGraham/Rostrum/releases/download/v${ROSTRUM_VERSION}/${filename}"
    
    log_info "Downloading Rostrum Server v${ROSTRUM_VERSION} for ${os}/${arch}..."
    
    # 创建安装目录
    mkdir -p "${ROSTRUM_INSTALL_DIR}/bin"
    
    # 下载文件
    curl -fsSL "${download_url}" -o "/tmp/${filename}"
    
    # 解压文件
    tar -xzf "/tmp/${filename}" -C "${ROSTRUM_INSTALL_DIR}/bin"
    
    # 设置执行权限
    chmod +x "${ROSTRUM_INSTALL_DIR}/bin/rostrum-server"
    
    # 清理临时文件
    rm -f "/tmp/${filename}"
    
    log_info "Rostrum Server installed to ${ROSTRUM_INSTALL_DIR}/bin/rostrum-server"
}

# 创建配置文件
create_config() {
    log_info "Creating configuration..."
    
    # 创建配置目录
    mkdir -p "${ROSTRUM_INSTALL_DIR}/config"
    
    # 创建配置文件
    cat > "${ROSTRUM_INSTALL_DIR}/config/server.json" <<EOF
{
    "port": ${ROSTRUM_PORT},
    "host": "${ROSTRUM_HOST}",
    "log_file": "${ROSTRUM_INSTALL_DIR}/logs/server.log",
    "workspace": "${ROSTRUM_WORKSPACE}",
    "max_file_size": 10485760,
    "allow_command": ${ROSTRUM_ALLOW_COMMAND}
}
EOF
    
    # 创建日志目录
    mkdir -p "${ROSTRUM_INSTALL_DIR}/logs"
    
    # 创建工作空间目录
    mkdir -p "${ROSTRUM_WORKSPACE}"
    
    log_info "Configuration created at ${ROSTRUM_INSTALL_DIR}/config/server.json"
}

# 创建systemd服务（如果可用）
create_systemd_service() {
    if command -v systemctl &> /dev/null; then
        log_info "Creating systemd service..."
        
        cat > /tmp/rostrum-server.service <<EOF
[Unit]
Description=Rostrum Server
After=network.target

[Service]
Type=simple
User=$(whoami)
ExecStart=${ROSTRUM_INSTALL_DIR}/bin/rostrum-server --config ${ROSTRUM_INSTALL_DIR}/config/server.json
Restart=always
RestartSec=5
Environment=ROSTRUM_PORT=${ROSTRUM_PORT}
Environment=ROSTRUM_HOST=${ROSTRUM_HOST}
Environment=ROSTRUM_WORKSPACE=${ROSTRUM_WORKSPACE}

[Install]
WantedBy=multi-user.target
EOF
        
        # 尝试安装systemd服务
        if sudo mv /tmp/rostrum-server.service /etc/systemd/system/ 2>/dev/null; then
            sudo systemctl daemon-reload
            sudo systemctl enable rostrum-server
            sudo systemctl start rostrum-server
            log_info "Systemd service created and started"
        else
            log_warn "Could not install systemd service (no sudo access)"
            log_info "You can start the server manually: ${ROSTRUM_INSTALL_DIR}/bin/rostrum-server --config ${ROSTRUM_INSTALL_DIR}/config/server.json"
        fi
    else
        log_warn "systemd not found, skipping service creation"
        log_info "You can start the server manually: ${ROSTRUM_INSTALL_DIR}/bin/rostrum-server --config ${ROSTRUM_INSTALL_DIR}/config/server.json"
    fi
}

# 验证安装
verify_installation() {
    log_info "Verifying installation..."
    
    if [ -x "${ROSTRUM_INSTALL_DIR}/bin/rostrum-server" ]; then
        log_info "✓ Rostrum Server installed successfully"
        log_info ""
        log_info "Installation directory: ${ROSTRUM_INSTALL_DIR}"
        log_info "Binary: ${ROSTRUM_INSTALL_DIR}/bin/rostrum-server"
        log_info "Config: ${ROSTRUM_INSTALL_DIR}/config/server.json"
        log_info "Workspace: ${ROSTRUM_WORKSPACE}"
        log_info ""
        log_info "To start the server:"
        log_info "  ${ROSTRUM_INSTALL_DIR}/bin/rostrum-server --config ${ROSTRUM_INSTALL_DIR}/config/server.json"
        log_info ""
        log_info "To test the server:"
        log_info "  curl http://${ROSTRUM_HOST}:${ROSTRUM_PORT}/api/ping"
    else
        log_error "Installation failed"
        exit 1
    fi
}

# 主函数
main() {
    log_info "Installing Rostrum Server v${ROSTRUM_VERSION}..."
    
    check_dependencies
    download_server
    create_config
    create_systemd_service
    verify_installation
    
    log_info "Installation completed!"
}

# 运行主函数
main "$@"
