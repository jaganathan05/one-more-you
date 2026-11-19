package com.onemoreyou.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object UninstallLock {
    fun isActive(context: Context): Boolean {
        val manager = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return manager.isAdminActive(component(context))
    }

    fun enableIntent(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "While this is on, ONE MORE YOU cannot be uninstalled.",
        )

    fun disable(context: Context) {
        context.getSystemService(DevicePolicyManager::class.java)
            ?.removeActiveAdmin(component(context))
    }

    private fun component(context: Context) = ComponentName(context, UninstallLockReceiver::class.java)
}

class UninstallLockReceiver : android.app.admin.DeviceAdminReceiver()
