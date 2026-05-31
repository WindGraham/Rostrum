plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // 纯 Kotlin 模块，无 Android 依赖
    // 协程核心
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    
    // 可选：依赖注入注解（javax.inject）
    implementation("javax.inject:javax.inject:1")
}
