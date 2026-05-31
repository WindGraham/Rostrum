package com.rostrum.core.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device-side integration test for the in-app Android APK build flow.
 *
 * This test creates a minimal Android project under app-private storage and
 * invokes AndroidApkBuildService directly to validate the end-to-end pipeline.
 */
@RunWith(AndroidJUnit4::class)
class AndroidBuildFlowDeviceTest {

    @Test(timeout = 600_000L)
    fun buildDebugApk_fromInAppFlow_succeeds() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        RuntimeInitializer.initialize(context, initializeAsync = false)

        val buildService = checkNotNull(RuntimeInitializer.androidApkBuildService) {
            "AndroidApkBuildService is not initialized"
        }

        val projectRoot = File(context.filesDir, "itest/minimal-android-app").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        createMinimalProject(projectRoot)

        val validation = buildService.validateProject(projectRoot.absolutePath)
        assertTrue("Validation failed: ${validation.errors}", validation.success)

        val result = buildService.build(
            ApkBuildRequest(
                projectRoot = projectRoot.absolutePath,
                buildType = BuildType.DEBUG,
                signing = null,
                installAfterBuild = false
            )
        )

        assertTrue(
            "Build failed in phase=${result.phase} error=${result.error} diagnostics=${result.diagnostics} logs=${result.logs}",
            result.success
        )

        val apkPath = result.signedApkPath ?: result.apkPath.orEmpty()
        val apkFile = File(apkPath)
        assertTrue("Signed APK not found: $apkPath", apkFile.exists() && apkFile.length() > 0L)
    }

    private fun createMinimalProject(root: File) {
        val javaDir = File(root, "src/main/java/com/example/minibuild").apply { mkdirs() }
        val resValuesDir = File(root, "src/main/res/values").apply { mkdirs() }
        val manifestFile = File(root, "src/main/AndroidManifest.xml").apply {
            parentFile?.mkdirs()
        }

        File(root, "omni.android.json").writeText(
            """
            {
              "applicationId": "com.example.minibuild",
              "versionCode": 1,
              "versionName": "1.0",
              "minSdk": 26,
              "targetSdk": 34,
              "compileSdk": 34,
              "mainActivity": ".MainActivity",
              "sourceDirs": ["src/main/java"],
              "kotlinSourceDirs": [],
              "mixedSourcePolicy": "disallow",
              "resourceDir": "src/main/res",
              "manifestPath": "src/main/AndroidManifest.xml",
              "assetDir": "src/main/assets",
              "jniLibsDir": "src/main/jniLibs",
              "localJars": [],
              "localAars": []
            }
            """.trimIndent()
        )

        manifestFile.writeText(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.minibuild">
                <application
                    android:label="@string/app_name"
                    android:allowBackup="true">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
            """.trimIndent()
        )

        File(resValuesDir, "strings.xml").writeText(
            """
            <resources>
                <string name="app_name">MiniBuild</string>
            </resources>
            """.trimIndent()
        )

        File(javaDir, "MainActivity.java").writeText(
            """
            package com.example.minibuild;

            import android.app.Activity;
            import android.os.Bundle;

            public class MainActivity extends Activity {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                }
            }
            """.trimIndent()
        )
    }
}
