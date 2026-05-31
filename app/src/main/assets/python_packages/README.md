# Python 预编译 Wheel 包目录

此目录存放为 Android aarch64 平台预编译的 Python wheel 包（.whl 文件）。

## 目标平台
- **Python**: CPython 3.14
- **ABI**: `cpython-314-aarch64-linux-android`
- **架构**: ARM64 (aarch64)

## 构建方法

使用 `scripts/build_native_wheels.sh` 脚本通过 Android NDK 交叉编译：

```bash
# 构建 Pillow
./scripts/build_native_wheels.sh pillow

# 构建所有支持的包
./scripts/build_native_wheels.sh all

# 仅从 PyPI 下载预编译 wheel（如果有）
./scripts/build_native_wheels.sh --download-only numpy
```

## Wheel 命名规范

```
{包名}-{版本}-{python标签}-{abi标签}-{平台标签}.whl
```

示例：
```
Pillow-10.4.0-cp314-cp314-linux_aarch64.whl
numpy-1.26.4-cp314-cp314-linux_aarch64.whl
```

## 安装机制

`NativePackageInstaller` 在运行时会：
1. 扫描此目录中的 .whl 文件
2. 匹配用户请求的包名
3. 解压到 Python 的 site-packages 目录
4. 自动为 .so 文件设置可执行权限

## 支持的包

参见 `NativePackageInstaller.NATIVE_PACKAGES` 中的完整列表。
