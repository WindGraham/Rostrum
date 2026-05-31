package com.rostrum.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rostrum.core.config.AppConfig
import com.rostrum.core.config.BuildSigningConfig
import com.rostrum.core.config.ConfigManagerImpl
import com.rostrum.core.runtime.ApkBuildRequest
import com.rostrum.core.runtime.BuildType
import com.rostrum.core.runtime.RuntimeInitializer
import com.rostrum.core.runtime.SigningConfig
import com.rostrum.core.runtime.model.BuildDiagnostic
import com.rostrum.core.runtime.model.BuildDiagnosticSeverity
import com.rostrum.core.runtime.model.BuildPhaseEvent
import com.rostrum.core.util.FileShareUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

private enum class BuildUiState {
    IDLE,
    VALIDATING,
    BUILDING,
    SUCCESS,
    INSTALL_READY,
    FAILED
}

@Composable
fun BuildPanel(
    currentPath: String,
    viewModel: MainViewModel,
    panePosition: PanePosition,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onSwitchPaneType: ((PanePosition, PaneContentType) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configManager = remember { ConfigManagerImpl.getInstance(context) }
    val localScope = rememberCoroutineScope()

    var appConfig by remember { mutableStateOf<AppConfig?>(null) }
    var projectRoot by remember { mutableStateOf("") }
    var buildType by remember { mutableStateOf(BuildType.DEBUG) }
    var uiState by remember { mutableStateOf(BuildUiState.IDLE) }
    var installAfterBuild by remember { mutableStateOf(false) }

    var storePassword by remember { mutableStateOf("") }
    var keyPassword by remember { mutableStateOf("") }

    var phaseEvents by remember { mutableStateOf<List<BuildPhaseEvent>>(emptyList()) }
    var diagnostics by remember { mutableStateOf<List<BuildDiagnostic>>(emptyList()) }
    var buildLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var signedApkPath by remember { mutableStateOf<String?>(null) }
    var installPermissionNeeded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appConfig = configManager.getAppConfig()
    }

    LaunchedEffect(currentPath) {
        if (projectRoot.isBlank()) {
            projectRoot = detectProjectRoot(currentPath) ?: currentPath
        }
    }

    val signingConfig = appConfig?.build?.releaseSigning ?: BuildSigningConfig()
    val debugAutoSign = appConfig?.build?.debugAutoSign ?: true

    fun openDiagnostic(diagnostic: BuildDiagnostic) {
        val file = diagnostic.file ?: return
        if (!File(file).exists()) return

        viewModel.openFileForViewingInPane(file, panePosition)
        onSwitchPaneType?.invoke(panePosition, PaneContentType.EDITOR)
    }

    fun buildRequest(): ApkBuildRequest {
        val config = appConfig?.build
        val releaseSigning = if (buildType == BuildType.RELEASE || (buildType == BuildType.DEBUG && !debugAutoSign)) {
            SigningConfig(
                keystorePath = signingConfig.keystorePath,
                storePassword = storePassword,
                keyAlias = signingConfig.keyAlias,
                keyPassword = keyPassword,
                storeType = signingConfig.storeType,
                v1Enabled = signingConfig.v1Enabled,
                v2Enabled = signingConfig.v2Enabled,
                v3Enabled = signingConfig.v3Enabled,
                v4Enabled = false
            )
        } else {
            null
        }

        return ApkBuildRequest(
            projectRoot = projectRoot,
            buildType = buildType,
            signing = releaseSigning,
            minSdk = config?.defaultMinSdk ?: 26,
            targetSdk = config?.defaultTargetSdk ?: 34,
            compileSdk = config?.defaultCompileSdk ?: 34,
            installAfterBuild = installAfterBuild
        )
    }

    fun startBuild() {
        val buildService = RuntimeInitializer.androidApkBuildService
        if (buildService == null) {
            scope.launch { snackbarHostState.showSnackbar("构建服务未初始化") }
            return
        }

        localScope.launch {
            uiState = BuildUiState.VALIDATING
            installPermissionNeeded = false
            phaseEvents = emptyList()
            diagnostics = emptyList()
            buildLogs = emptyList()
            signedApkPath = null

            val validateResult = buildService.validateProject(projectRoot)
            if (!validateResult.success) {
                uiState = BuildUiState.FAILED
                diagnostics = validateResult.errors.map {
                    BuildDiagnostic(
                        phase = com.rostrum.core.runtime.model.BuildPhase.VALIDATE,
                        message = it
                    )
                }
                return@launch
            }

            uiState = BuildUiState.BUILDING
            val result = buildService.build(buildRequest())
            phaseEvents = result.phaseEvents
            diagnostics = result.diagnostics
            buildLogs = result.logs.map { log ->
                "${log.stage}: ${if (log.success) "OK" else "FAIL"} (${log.durationMs}ms) ${log.error.ifBlank { log.output }}"
            }

            if (result.success) {
                signedApkPath = result.signedApkPath ?: result.apkPath
                uiState = BuildUiState.INSTALL_READY
                scope.launch {
                    snackbarHostState.showSnackbar("构建成功: ${signedApkPath ?: "(无路径)"}")
                }

                if (installAfterBuild) {
                    signedApkPath?.let { path ->
                        val installResult = FileShareUtils.installApk(context, File(path))
                        installPermissionNeeded = installResult.requiresUserPermission
                        if (installResult.launched) {
                            uiState = BuildUiState.SUCCESS
                        }
                    }
                }
            } else {
                uiState = BuildUiState.FAILED
                scope.launch {
                    snackbarHostState.showSnackbar(result.error ?: "构建失败")
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Android APK 构建", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = projectRoot,
            onValueChange = { projectRoot = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("项目根目录") },
            singleLine = true
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("构建类型")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = buildType == BuildType.DEBUG, onClick = { buildType = BuildType.DEBUG })
                    Text("Debug")
                    Spacer(Modifier.width(12.dp))
                    RadioButton(selected = buildType == BuildType.RELEASE, onClick = { buildType = BuildType.RELEASE })
                    Text("Release")
                }

                if (buildType == BuildType.DEBUG) {
                    Text(
                        if (debugAutoSign) "Debug 自动签名已启用" else "Debug 自动签名已关闭（需手动签名配置）",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (buildType == BuildType.RELEASE || !debugAutoSign) {
                    Text("签名配置来源：设置页中的发布签名配置", style = MaterialTheme.typography.bodySmall)
                    Text("keystore: ${if (signingConfig.keystorePath.isBlank()) "未配置" else signingConfig.keystorePath}", style = MaterialTheme.typography.bodySmall)
                    Text("alias: ${if (signingConfig.keyAlias.isBlank()) "未配置" else signingConfig.keyAlias}", style = MaterialTheme.typography.bodySmall)

                    OutlinedTextField(
                        value = storePassword,
                        onValueChange = { storePassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Store Password（仅内存）") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = keyPassword,
                        onValueChange = { keyPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Key Password（仅内存）") },
                        singleLine = true
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(
                checked = installAfterBuild,
                onCheckedChange = { installAfterBuild = it }
            )
            Text("构建后立即安装")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { startBuild() },
                enabled = uiState != BuildUiState.BUILDING && projectRoot.isNotBlank()
            ) {
                if (uiState == BuildUiState.BUILDING || uiState == BuildUiState.VALIDATING) {
                    CircularProgressIndicator(modifier = Modifier.width(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (uiState == BuildUiState.BUILDING) "构建中" else "开始构建")
            }

            OutlinedButton(
                onClick = {
                    RuntimeInitializer.androidApkBuildService?.cancelCurrentBuild()
                    uiState = BuildUiState.FAILED
                },
                enabled = uiState == BuildUiState.BUILDING
            ) {
                Text("取消")
            }

            OutlinedButton(
                onClick = {
                    localScope.launch {
                        RuntimeInitializer.androidApkBuildService?.clean(projectRoot)
                        scope.launch { snackbarHostState.showSnackbar("已清理 .omnimaster/build") }
                    }
                },
                enabled = projectRoot.isNotBlank()
            ) {
                Text("清理")
            }
        }

        if (installPermissionNeeded) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("系统未授予“安装未知应用”权限", color = MaterialTheme.colorScheme.onErrorContainer)
                    OutlinedButton(onClick = { FileShareUtils.openUnknownAppsSettings(context) }) {
                        Text("去授权")
                    }
                }
            }
        }

        if (signedApkPath != null) {
            Card {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("构建产物")
                    Text(signedApkPath!!, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val result = FileShareUtils.installApk(context, File(signedApkPath!!))
                            installPermissionNeeded = result.requiresUserPermission
                        }) {
                            Text("安装 APK")
                        }
                    }
                }
            }
        }

        if (phaseEvents.isNotEmpty()) {
            Card {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("构建阶段")
                    phaseEvents.takeLast(20).forEach {
                        Text(
                            text = "${it.phase} ${it.state} ${it.message}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (diagnostics.isNotEmpty()) {
            Card {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("诊断信息 (${diagnostics.size})")
                    diagnostics.take(30).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !item.file.isNullOrBlank()) { openDiagnostic(item) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val messageColor = when (item.severity) {
                                    BuildDiagnosticSeverity.ERROR -> MaterialTheme.colorScheme.error
                                    BuildDiagnosticSeverity.WARNING -> androidx.compose.ui.graphics.Color(0xFFB26A00)
                                    BuildDiagnosticSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    text = item.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = messageColor
                                )
                                val location = buildString {
                                    append(item.file ?: "")
                                    if (item.line != null) append(":${item.line}")
                                    if (item.column != null) append(":${item.column}")
                                }
                                if (location.isNotBlank()) {
                                    Text(location, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (!item.file.isNullOrBlank()) {
                                Text("定位", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        if (buildLogs.isNotEmpty()) {
            Card {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("构建日志")
                    buildLogs.takeLast(40).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (uiState == BuildUiState.IDLE && diagnostics.isEmpty() && buildLogs.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("选择项目目录后即可开始验证和构建")
            }
        }
    }
}

private fun detectProjectRoot(path: String): String? {
    if (path.isBlank()) return null
    var current = File(path).let { if (it.isDirectory) it else it.parentFile }
    while (current != null) {
        if (File(current, "omni.android.json").exists()) {
            return current.absolutePath
        }
        current = current.parentFile
    }
    return null
}
