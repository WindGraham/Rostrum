package com.rostrum.util

import android.content.Context

/** Compatibility facade for UI that still links to the removed privileged bridge. */
object PrivilegedBridgeManager {
    fun getAdbStartCommand(context: Context): String {
        return "Privileged bridge support is disabled in this build."
    }

    fun getStartGuide(context: Context): String {
        return "The internal privileged bridge was removed from this build. Use SAF or standard storage access instead."
    }

    suspend fun startWithRoot(context: Context): Boolean = false
}
