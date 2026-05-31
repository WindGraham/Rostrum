# GDB 初始化文件 - Android NDK 调试
# 在 GDB 中使用: source scripts/gdb_init.gdb

# 设置架构
set architecture aarch64

# 设置符号搜索路径
set solib-absolute-prefix /

# 常用断点位置（根据实际代码调整）
# SPAKE2 配对相关
# break PairingContext_Constructor
# break PairingContext_ProcessMsg
# break PairingContext_GetMsg
# break PairingContext_GetKey

# 日志输出相关
# break __android_log_print

# 显示设置
set print pretty on
set print elements 0
set print null-stop on

# 历史记录
set history save on
set history size 1000

# 反汇编风格
set disassembly-flavor intel

# 显示当前函数
define c
    continue
    frame
end
document c
Continue and show current frame
end

# 显示变量值（C++ 风格）
define pv
    print $arg0
    print/x $arg0
end
document pv
Print variable in decimal and hex
end

# 显示数组
define pa
    print $arg0@$arg1
end
document pa
Print array: pa <array> <count>
end

# 显示内存
define pm
    x/16x $arg0
end
document pm
Print memory: pm <address>
end

# 显示调用栈（带参数）
define bt_full
    backtrace full
end
document bt_full
Show full backtrace with local variables
end

