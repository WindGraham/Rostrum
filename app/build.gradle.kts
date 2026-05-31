plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// 使用 Android Studio 的 Java 21 作为工具链
// 但编译目标仍然是 Java 17（通过 compileOptions 设置）
// kotlin {
//     jvmToolchain(17)
// }

android {
    namespace = "com.rostrum"
    compileSdk = 34  // 更新到 34 以满足依赖库要求（androidx.core:core-ktx:1.12.0 等）

    defaultConfig {
        applicationId = "com.rostrum"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isJniDebuggable = true  // 启用 JNI 调试
            isMinifyEnabled = false
            ndk {
                debugSymbolLevel = "FULL"  // 包含完整调试符号
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    kotlinOptions {
        jvmTarget = "21"
    }
    
    buildFeatures {
        compose = true
        aidl = true  // 启用 AIDL
        prefab = true
        buildConfig = true
    }

    // Android 资源打包选项
    androidResources {
        // 确保 .whl 文件（zip格式）不被压缩，以便 AssetManager 正确读取
        noCompress += listOf("whl", "egg")
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // 排除重复的 META-INF 文件（POI、PDFBox、AndroidPdfViewer 都包含）
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/proguard/androidx-*.pro"
            excludes += "/META-INF/androidx.*"
            excludes += "/META-INF/services/*"
            excludes += "/META-INF/eclipse.inf"
            excludes += "/about_files/*"
            excludes += "/plugin.xml"
            excludes += "/plugin.properties"
        }
        // 排除不支持的 riscv64 ABI
        jniLibs {
            // 必须设置为 true，否则 native libraries 不会被提取到文件系统
            // Python 可执行文件 (libpython_main.so) 需要被提取才能执行
            useLegacyPackaging = true
            excludes += listOf(
                "**/riscv64/**",
                "**/lib/riscv64/**",
                "**/armeabi/**",  // 排除旧的 armeabi
                "**/lib/armeabi/**"
            )
        }
    }
    
    // 在配置阶段排除 riscv64 ABI 和解决依赖冲突
    configurations.all {
        resolutionStrategy {
            eachDependency {
                // 强制使用 AndroidX 版本，避免 Support 库冲突
                if (requested.group == "com.android.support") {
                    useVersion("28.0.0")
                    because("AndroidPdfViewer 需要 Support 库，但我们已经全局排除了")
                }
            }
            // 优先使用 AndroidX 版本
            force("androidx.core:core:1.12.0")
            force("androidx.versionedparcelable:versionedparcelable:1.1.1")
        }
    }
}

// KSP 配置
ksp {
    // Room schema export location
    arg("room.schemaLocation", "${projectDir}/schemas")
}

// 全局排除重复的 annotations 库和旧的 Android Support 库
configurations.all {
    exclude(group = "org.jetbrains", module = "annotations-java5")
    // 排除旧的 Android Support 库（使用 AndroidX）
    exclude(group = "com.android.support", module = "support-compat")
    exclude(group = "com.android.support", module = "support-core-utils")
    exclude(group = "com.android.support", module = "support-core-ui")
    exclude(group = "com.android.support", module = "support-fragment")
    exclude(group = "com.android.support", module = "support-v4")
    exclude(group = "com.android.support", module = "versionedparcelable")
    // 排除 rikkax appcompat，使用标准 androidx.appcompat
    exclude(group = "dev.rikka.rikkax.appcompat", module = "appcompat")
}

dependencies {
    // Core modules
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    
    // Extension API 库
    implementation(project(":extension-api"))
    
    // Terminal module (PRoot + PTY)
    implementation(project(":terminal"))
    
    // Room Database - 解决"数据库存储层薄弱"问题
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // DataStore - 替换SharedPreferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Built-in Plugins (Coupled mode for development)
    // implementation(project(":plugins:pdf-preview"))  // 暂时禁用避免 AAPT2 冲突
    
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1") {
        exclude(group = "dev.rikka.rikkax.appcompat")
    }
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.activity:activity:1.8.2")
    implementation("androidx.activity:activity-ktx:1.8.2")
    
    // Emoji2
    implementation("androidx.emoji2:emoji2:1.4.0")
    
    // Compose - 使用兼容的 BOM 版本
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    
    // Hilt - 依赖注入
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    // Navigation 传递依赖，显式指定版本
    implementation("androidx.navigation:navigation-common:2.7.6")
    implementation("androidx.navigation:navigation-runtime:2.7.6")
    implementation("androidx.navigation:navigation-common-ktx:2.7.6")
    implementation("androidx.navigation:navigation-runtime-ktx:2.7.6")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Room Database（暂时注释，等需要时再启用）
    // implementation("androidx.room:room-runtime:2.6.1")
    // implementation("androidx.room:room-ktx:2.6.1")
    // kapt("androidx.room:room-compiler:2.6.1")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
    
    // JavaScript Engine (Rhino)
    implementation("org.mozilla:rhino:1.7.15")

    // Java language engine (ECJ + JDT Core, EPL-2.0)
    implementation("org.eclipse.jdt:org.eclipse.jdt.core:3.33.0")
    implementation("org.eclipse.jdt:ecj:3.33.0")
    // Android runtime lacks java.compiler APIs required by ECJ (javax.annotation.processing, javax.lang.model, javax.tools).
    implementation(files("libs/java-compiler-api-android.jar"))
    
    // Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    
    // DocumentFile (for SAF access to Android/data)
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    // Libsu (Shell management)
    implementation("com.github.topjohnwu.libsu:core:5.2.1")
    
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Bouncy Castle for SSH key handling and cryptographic utilities.
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.77")
    
    // Terminal Emulator
    // 暂时禁用 emulatorview 模块（构建有问题，且当前使用的是自定义 TerminalView）
    // implementation(project(":emulatorview"))
    
    // ==================== 第三方功能库 ====================
    
    // Markdown 渲染 (Apache-2.0) - Markdown 文件预览
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:syntax-highlight:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    
    // ANR 监控 (Apache-2.0) - 检测应用无响应
    implementation("com.github.anrwatchdog:anrwatchdog:1.4.0")
    
    // 文本差异比较 (Apache-2.0) - 代码对比、文件差异显示
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    
    // Apache Commons 工具库 (Apache-2.0)
    implementation("commons-io:commons-io:2.15.1")  // 文件 IO 操作
    implementation("org.apache.commons:commons-text:1.11.0")  // 字符串处理
    
    // GIF 动图支持 (MIT) - 显示 GIF 动图
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.28")
    
    // mDNS 局域网设备发现 (Apache-2.0) - 排除冲突的 annotations
    implementation("org.jmdns:jmdns:3.5.9") {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    
    // SSH 协议支持 (BSD-2-Clause)
    implementation("com.jcraft:jsch:0.1.55")
    
    // FTP 协议支持 (Apache-2.0)
    implementation("commons-net:commons-net:3.10.0")
    
    // ==================== Office文档处理 (Apache-2.0) ====================
    
    // Apache POI - Office文档读写
    // 使用标准 Apache POI 库（Android 兼容）
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.poi:poi-scratchpad:5.2.5")
    
    // PDF 预览使用 WebView（避免 AndroidPdfViewer 的 Support 库冲突）
    // AndroidPdfViewer 依赖旧的 Support 库，与 AndroidX 冲突
    // 改用 WebView + PDF.js 或系统 PDF 查看器
    // implementation("com.github.barteksc:android-pdf-viewer:3.2.0-beta.1")  // 暂时注释，避免依赖冲突
    
    // JS 代码格式化 (MIT)
    // implementation("com.nicksay:js-beautify:0.1.0")  // 暂时注释，需要时再启用
    
    // Zstd 压缩 (BSD) - 高性能压缩算法
    implementation("com.github.luben:zstd-jni:1.5.5-11")
    
    // Debug tools
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
