package com.onemoreyou.service

import android.os.Handler
import android.os.Looper

object ReevaluateBus {
    @Volatile
    private var listener: (() -> Unit)? = null

    fun setListener(callback: (() -> Unit)?) {
        listener = callback
    }

    fun ping() {
        val callback = listener ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback()
        } else {
            Handler(Looper.getMainLooper()).post(callback)
        }
    }
}
