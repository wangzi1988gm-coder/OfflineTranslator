package com.example.offlinetranslator.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object Permission {
    fun isGranted(context: Context, vararg perms: String): Boolean = perms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
