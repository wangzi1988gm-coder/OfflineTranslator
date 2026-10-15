package com.example.offlinetranslator.util

import android.util.Log

object Logger {
    fun d(tag: String, msg: String) { Log.d(tag, msg) }
    fun e(tag: String, msg: String, t: Throwable? = null) { Log.e(tag, msg, t) }
    fun w(tag: String, msg: String) { Log.w(tag, msg) }
}
