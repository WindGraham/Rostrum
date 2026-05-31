pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Rostrum"
include(":app")
include(":extension-api")

// Terminal module (PRoot + PTY)
include(":terminal")
project(":terminal").projectDir = file("terminal")

// 暂时禁用 emulatorview 模块（构建有问题，且当前使用的是自定义 TerminalView）
// include(":emulatorview")
// project(":emulatorview").projectDir = file("third_party/Android-Terminal-Emulator/emulatorview")


// ========== Core Modules ==========
include(":core:domain")
project(":core:domain").projectDir = file("core/domain")

include(":core:data")
project(":core:data").projectDir = file("core/data")

include(":core:common")
project(":core:common").projectDir = file("core/common")

include(":core:designsystem")
project(":core:designsystem").projectDir = file("core/designsystem")
