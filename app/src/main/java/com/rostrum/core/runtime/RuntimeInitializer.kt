package com.rostrum.core.runtime

import android.content.Context
import android.util.Log
import com.rostrum.core.runtime.impl.AndroidApkBuildServiceImpl
import com.rostrum.core.runtime.impl.FileExecutionServiceImpl
import com.rostrum.core.runtime.impl.JavaExecutionServiceImpl
import com.rostrum.core.runtime.impl.JavaFileExecutor
import com.rostrum.core.runtime.impl.KotlinExecutionServiceImpl
import com.rostrum.core.runtime.impl.KotlinFileExecutor
import com.rostrum.core.runtime.impl.PythonExecutionServiceImpl
import com.rostrum.core.runtime.impl.PythonFileExecutor
import com.rostrum.core.runtime.impl.ToolchainManager
import com.rostrum.core.shell.python.PythonRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lightweight runtime service registry used by remaining build/execution UI.
 * Privileged shell integration is intentionally disabled in the stripped app.
 */
object RuntimeInitializer {
    private const val TAG = "RuntimeInitializer"

    @Volatile
    private var initialized = false

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var pythonExecutionService: PythonExecutionService? = null
        private set
    var nativePackageInstaller: NativePackageInstaller? = null
        private set
    var fileExecutionService: FileExecutionService? = null
        private set
    var javaExecutionService: JavaExecutionService? = null
        private set
    var kotlinExecutionService: KotlinExecutionService? = null
        private set
    var androidApkBuildService: AndroidApkBuildService? = null
        private set
    var privilegedShellService: PrivilegedShellService? = null
        private set

    fun initialize(context: Context) {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            val appContext = context.applicationContext
            val toolchainManager = ToolchainManager(appContext)
            val pythonRuntime = PythonRuntime(appContext)
            val pythonService = PythonExecutionServiceImpl(pythonRuntime)
            val javaService = JavaExecutionServiceImpl(appContext, toolchainManager)
            val kotlinService = KotlinExecutionServiceImpl(toolchainManager)
            val fileService = FileExecutionServiceImpl().apply {
                registerExecutor(PythonFileExecutor(pythonService))
                registerExecutor(JavaFileExecutor(javaService))
                registerExecutor(KotlinFileExecutor(kotlinService))
            }

            pythonExecutionService = pythonService
            nativePackageInstaller = NativePackageInstaller(appContext, pythonRuntime)
            fileExecutionService = fileService
            javaExecutionService = javaService
            kotlinExecutionService = kotlinService
            androidApkBuildService = AndroidApkBuildServiceImpl(
                context = appContext,
                toolchainManager = toolchainManager,
                javaExecutionService = javaService,
                kotlinExecutionService = kotlinService
            )
            privilegedShellService = null
            initialized = true

            scope.launch {
                runCatching { pythonService.initialize() }
                    .onFailure { Log.w(TAG, "Python runtime initialization failed", it) }
                runCatching { javaService.initialize() }
                    .onFailure { Log.w(TAG, "Java runtime initialization failed", it) }
                runCatching { kotlinService.initialize() }
                    .onFailure { Log.w(TAG, "Kotlin runtime initialization failed", it) }
            }

            Log.d(TAG, "Runtime services initialized")
        }
    }

    fun cleanup() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        pythonExecutionService = null
        nativePackageInstaller = null
        fileExecutionService = null
        javaExecutionService = null
        kotlinExecutionService = null
        androidApkBuildService = null
        privilegedShellService = null
        initialized = false
        Log.d(TAG, "Runtime services cleaned up")
    }

    fun getStatusInfo(): Map<String, Any?> = mapOf(
        "initialized" to initialized,
        "pythonExecutionService" to (pythonExecutionService != null),
        "nativePackageInstaller" to (nativePackageInstaller != null),
        "fileExecutionService" to (fileExecutionService != null),
        "javaExecutionService" to (javaExecutionService != null),
        "kotlinExecutionService" to (kotlinExecutionService != null),
        "androidApkBuildService" to (androidApkBuildService != null),
        "privilegedShellService" to false
    )
}
