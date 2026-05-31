# 开源协议声明

本文档列出了 OmniMaster 项目中使用的所有需要保留版权声明和许可证文本的开源库。

## 重要说明

根据各开源协议的要求，使用以下库时**必须**：
- 保留原始版权声明
- 保留许可证文本
- 在应用或文档中明确标注使用的库及其许可证

---

## Apache License 2.0

以下库使用 Apache License 2.0，需要保留版权声明和许可证文本：

### AndroidX 库（Google）
- `androidx.core:core-ktx:1.12.0`
- `androidx.core:core:1.12.0`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.6.2`
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2`
- `androidx.lifecycle:lifecycle-runtime-compose:2.6.2`
- `androidx.activity:activity-compose:1.8.2`
- `androidx.activity:activity:1.8.2`
- `androidx.activity:activity-ktx:1.8.2`
- `androidx.emoji2:emoji2:1.4.0`
- `androidx.compose:compose-bom:2024.02.00`
- `androidx.compose.ui:ui`
- `androidx.compose.ui:ui-graphics`
- `androidx.compose.ui:ui-tooling-preview`
- `androidx.compose.material3:material3`
- `androidx.compose.material:material-icons-extended`
- `androidx.compose.animation:animation`
- `androidx.navigation:navigation-compose:2.7.6`
- `androidx.navigation:navigation-common:2.7.6`
- `androidx.navigation:navigation-runtime:2.7.6`
- `androidx.navigation:navigation-common-ktx:2.7.6`
- `androidx.navigation:navigation-runtime-ktx:2.7.6`
- `androidx.datastore:datastore-preferences:1.0.0`
- `androidx.documentfile:documentfile:1.0.1`
- `androidx.test.ext:junit:1.1.5`
- `androidx.test.espresso:espresso-core:3.5.1`

**版权声明示例**：
```
Copyright 2018 The Android Open Source Project
```

### Square 库
- `com.squareup.retrofit2:retrofit:2.9.0`
- `com.squareup.retrofit2:converter-gson:2.9.0`
- `com.squareup.okhttp3:logging-interceptor:4.12.0`

**版权声明示例**：
```
Copyright 2013 Square, Inc.
```

### Markwon（Markdown 渲染）
- `io.noties.markwon:core:4.6.2`
- `io.noties.markwon:ext-strikethrough:4.6.2`
- `io.noties.markwon:ext-tables:4.6.2`
- `io.noties.markwon:syntax-highlight:4.6.2`
- `io.noties.markwon:linkify:4.6.2`

**版权声明示例**：
```
Copyright 2017 Dimitry Ivanov
```

### ANR Watchdog
- `com.github.anrwatchdog:anrwatchdog:1.4.0`

**版权声明示例**：
```
Copyright 2015 Salomon BRYS
```

### Java Diff Utils
- `io.github.java-diff-utils:java-diff-utils:4.12`

**版权声明示例**：
```
Copyright 2011-2015 java-diff-utils contributors
```

### Apache Commons
- `commons-io:commons-io:2.15.1`
- `org.apache.commons:commons-text:1.11.0`
- `commons-net:commons-net:3.10.0`

**版权声明示例**：
```
Copyright 2002-2024 The Apache Software Foundation
```

### Apache POI（Office 文档处理）
- `org.apache.poi:poi:5.2.5`
- `org.apache.poi:poi-ooxml:5.2.5`
- `org.apache.poi:poi-scratchpad:5.2.5`

**版权声明示例**：
```
Copyright 2002-2024 The Apache Software Foundation
```

### JmDNS（mDNS 局域网设备发现）
- `org.jmdns:jmdns:3.5.9`

**版权声明示例**：
```
Copyright 2003-2015 JmDNS contributors
```

### Coil（图片加载）
- `io.coil-kt:coil-compose:2.5.0`

**版权声明示例**：
```
Copyright 2018 Coil Contributors
```

### Accompanist（权限处理）
- `com.google.accompanist:accompanist-permissions:0.32.0`

**版权声明示例**：
```
Copyright 2020 The Android Open Source Project
```

### Hilt / Dagger（依赖注入）
- `com.google.dagger:hilt-android:2.50`
- `com.google.dagger:hilt-android-compiler:2.50`
- `androidx.hilt:hilt-navigation-compose:1.1.0`

**版权声明示例**：
```
Copyright 2020 The Dagger Authors
```

### Room（数据库）
- `androidx.room:room-runtime:2.6.1`
- `androidx.room:room-ktx:2.6.1`
- `androidx.room:room-compiler:2.6.1`

**版权声明示例**：
```
Copyright 2017 The Android Open Source Project
```

## MIT License

以下库使用 MIT License，需要保留版权声明和许可证文本：

### Kotlin 协程
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0`

**版权声明示例**：
```
Copyright 2017-2024 JetBrains s.r.o. and respective contributors
```

### Gson
- `com.google.code.gson:gson:2.10.1`

**版权声明示例**：
```
Copyright 2008 Google Inc.
```

### Android GIF Drawable
- `pl.droidsonroids.gif:android-gif-drawable:1.2.28`

**版权声明示例**：
```
Copyright 2013-2016 Karol Wrótniak, Droids on Roids LLC
```

### JUnit（测试）
- `junit:junit:4.13.2`

**版权声明示例**：
```
Copyright 2002-2024 JUnit Project
```

---

## BSD-2-Clause License

以下库使用 BSD-2-Clause License，需要保留版权声明：

### JSch（SSH 协议支持）
- `com.jcraft:jsch:0.1.55`

**版权声明示例**：
```
Copyright (c) 2002-2015 Atsuhiko Yamanaka, JCraft,Inc.
```

**注意**：JSch 还包含以下子组件的许可证：
- jBCrypt: BSD-2-Clause
- JZlib: zlib License

---

## BSD-3-Clause License / BSD License

以下库使用 BSD-3-Clause 或 BSD License，需要保留版权声明：

### Zstd JNI（压缩算法）
- `com.github.luben:zstd-jni:1.5.5-11`

**版权声明示例**：
```
Copyright (c) 2016-2024 Luben Karavelov
```

---

## MPL-2.0 (Mozilla Public License 2.0)

以下库使用 MPL-2.0，需要保留版权声明和许可证文本：

### Rhino（JavaScript 引擎）
- `org.mozilla:rhino:1.7.15`

**版权声明示例**：
```
Copyright 1997-2000 mozilla.org contributors
```

---

## Apache License 2.0 / MIT License（双重许可）

以下库可能使用双重许可，需要根据实际使用的许可证保留相应声明：

### Jsoup（HTML 解析）
- `org.jsoup:jsoup:1.17.2`

**版权声明示例**：
```
Copyright 2009-2024 Jonathan Hedley
```

---

## Bouncy Castle License

以下库使用 Bouncy Castle License（类似 MIT），需要保留版权声明：

### Bouncy Castle（加密库）
- `org.bouncycastle:bcprov-jdk18on:1.77`
- `org.bouncycastle:bcpkix-jdk18on:1.77`

**版权声明示例**：
```
Copyright (c) 2000-2024 The Legion of the Bouncy Castle Inc.
```

**许可证说明**：
Bouncy Castle 使用自己的许可证，类似于 MIT License，但有一些额外的限制。需要保留版权声明。

---

## Libsu License

以下库使用 Apache-2.0 License，需要保留版权声明：

### Libsu（Shell 管理）
- `com.github.topjohnwu.libsu:core:5.2.1`

**版权声明示例**：
```
Copyright 2017-2024 topjohnwu
```

---

## 第三方代码库（thirdparty 目录）

以下代码库位于 `thirdparty` 目录，需要特别关注：

### VSCode（MIT License）
- 位置：`vscode-main/`
- 许可证：MIT License
- **版权声明**：
```
Copyright (c) 2015 - present Microsoft Corporation
```

### Fernflower（Apache-2.0）
- 位置：`thirdparty/Fernflower/`
- 许可证：Apache-2.0
- **版权声明**：
```
Copyright 2000-2024 JetBrains s.r.o.
```

### Vineflower（Apache-2.0）
- 位置：`thirdparty/Vineflower/`
- 许可证：Apache-2.0
- **版权声明**：
```
Copyright 2000-2024 Vineflower contributors
```

### Procyon（Apache-2.0）
- 位置：`thirdparty/Procyon/`
- 许可证：Apache-2.0
- **版权声明**：
```
Copyright 2015 Mike Strobel
```

### Markwon（Apache-2.0）
- 位置：`thirdparty/Markwon/`
- 许可证：Apache-2.0
- **版权声明**：
```
Copyright 2017 Dimitry Ivanov
```

### OkHttp（Apache-2.0）
- 位置：`thirdparty/okhttp/`
- 许可证：Apache-2.0
- **版权声明**：
```
Copyright 2013 Square, Inc.
```

### Guava（Apache-2.0）
- 位置：`thirdparty/guava/`
- 许可证：Apache-2.0
- **版权声明**：
```
Copyright 2010 The Guava Authors
```

### Android Terminal Emulator（Apache-2.0）
- 位置：`thirdparty/Android-Terminal-Emulator/`
- 许可证：Apache-2.0
- **版权声明**：
```
Copyright 2011-2015 Android Terminal Emulator contributors
```

### LSPatch（GPL-3.0）- ⚠️ 未实际使用
- 位置：`thirdparty/LSPatch/`
- 许可证：GPL-3.0
- **状态**：此代码库已下载到 `thirdparty` 目录，但**未在项目中使用**
- **注意**：如果将来需要使用此代码，需要注意 GPL-3.0 是 copyleft 许可证，使用此代码需要：
  - 保留完整的 GPL-3.0 许可证文本
  - 如果修改代码，需要公开源代码
  - 整个项目可能需要采用 GPL-3.0 许可证

**版权声明**（如果使用）：
```
Copyright 2021-2024 LSPosed contributors
```

---

## Android Java/APK 构建工具链（新增）

以下组件用于 Android 端 Java 编译与 APK 构建流程（V1）：

### Eclipse JDT Core / ECJ（EPL-2.0）
- 来源：`https://github.com/eclipse-jdt/eclipse.jdt.core`
- 用途：Java 语义分析与编译
- 许可证：EPL-2.0

### AAPT2（Apache-2.0）
- 来源：Android SDK / AOSP
- 用途：资源编译与链接
- 许可证：Apache-2.0

### D8/R8（BSD-3-Clause）
- 来源：Android SDK / AOSP prebuilts
- 用途：字节码转 DEX 与优化
- 许可证：BSD-3-Clause

### zipalign（Apache-2.0）
- 来源：AOSP build tools
- 用途：APK 对齐
- 许可证：Apache-2.0

### apksig/apksigner（Apache-2.0）
- 来源：AOSP tools/apksig
- 用途：APK 签名与校验
- 许可证：Apache-2.0

### Termux（GPL-3.0）策略声明
- 说明：Termux 相关代码/运行时不纳入 OmniMaster 构建与运行链路。
- 原因：GPL-3.0 copyleft 风险，不符合当前闭源商用分发策略。

## 许可证文本要求

### Apache License 2.0
必须在应用或文档中包含完整的 Apache License 2.0 文本。可以在应用设置页面、关于页面或单独的"开源许可"页面中展示。

### MIT License
必须在应用或文档中包含完整的 MIT License 文本，包括版权声明和许可声明。

### BSD License
必须保留版权声明和许可声明。

### GPL-3.0
**当前状态**：项目中未实际使用 GPL-3.0 许可的代码。

如果将来需要使用 GPL-3.0 许可的代码（如 LSPatch），必须：
1. 保留完整的 GPL-3.0 许可证文本
2. 如果修改代码，必须公开源代码
3. 整个项目可能需要采用 GPL-3.0 许可证（取决于使用方式）

### EPL-2.0
需要保留版权声明与许可证声明。

适用组件：
- `org.eclipse.jdt:org.eclipse.jdt.core:3.44.0`（ECJ / JDT Core）

### Android 工具链二进制
工具链资产（`aapt2` / `d8` / `zipalign` / `apksigner` / `android.jar`）的来源、版本、SHA256 与许可证见：
- `app/src/main/assets/toolchains/android/arm64-v8a/manifest.json`

---

## 建议的实现方式

1. **在应用中添加"开源许可"页面**：
   - 列出所有使用的开源库
   - 显示每个库的许可证类型
   - 提供许可证全文链接或展示

2. **在 README.md 中添加声明**：
   - 列出主要依赖库
   - 提供许可证信息链接

3. **在应用关于页面添加**：
   - "使用开源库"链接
   - 指向开源许可页面

4. **在发布说明中提及**：
   - 感谢所有开源库的贡献者

---

## 注意事项

1. **动态链接 vs 静态链接**：
   - 大多数 Android 库都是动态链接，Apache-2.0 和 MIT 许可证通常只需要保留版权声明
   - GPL/LGPL 许可证需要特别注意，可能需要公开源代码

2. **第三方代码库**：
   - `thirdparty` 目录中的代码需要特别关注
   - 如果修改了这些代码，需要保留修改说明

3. **许可证兼容性**：
   - 确保所有许可证兼容
   - GPL-3.0 许可证可能影响整个项目的许可证选择

4. **定期更新**：
   - 依赖库更新时，检查许可证是否有变化
   - 添加新库时，更新此文档

---

## 参考资源

- [Apache License 2.0 全文](https://www.apache.org/licenses/LICENSE-2.0)
- [MIT License 全文](https://opensource.org/licenses/MIT)
- [BSD-2-Clause License](https://opensource.org/licenses/BSD-2-Clause)
- [BSD-3-Clause License](https://opensource.org/licenses/BSD-3-Clause)
- [GPL-3.0 License](https://www.gnu.org/licenses/gpl-3.0.html)
- [MPL-2.0 License](https://www.mozilla.org/en-US/MPL/2.0/)

---

**最后更新**：2024年（请根据实际情况更新）

**维护者**：OmniMaster 开发团队
