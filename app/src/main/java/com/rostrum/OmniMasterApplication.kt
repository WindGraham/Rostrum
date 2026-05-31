package com.rostrum

import android.app.Application
import android.util.Log
import com.rostrum.ui.bookmarks.BookmarkManager
import com.rostrum.ui.history.FileHistoryManager
import com.rostrum.ui.theme.ThemeManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class OmniMasterApplication : Application() {
    companion object {
        private const val TAG = "RostrumApplication"

        fun getPluginManager(): com.rostrum.core.plugin.external.ExternalPluginManager? = null
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { _, exception ->
            Log.e(TAG, "Uncaught exception", exception)
        }

        Log.d(TAG, "Rostrum Application starting...")
        BookmarkManager.init(this)
        FileHistoryManager.init(this)

        applicationScope.launch {
            ThemeManager.initialize(this@OmniMasterApplication)
            Log.d(TAG, "ThemeManager initialized, style=${ThemeManager.themeStyle}")
        }
    }
}
