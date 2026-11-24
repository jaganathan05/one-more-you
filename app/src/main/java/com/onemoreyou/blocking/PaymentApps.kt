package com.onemoreyou.blocking

object PaymentApps {
    fun mustPause(packageName: String): Boolean {
        return packageName == PACKAGE_INSTALLER ||
            packageName == GOOGLE_PACKAGE_INSTALLER ||
            packageName == GOOGLE_WALLET ||
            packageName == GPAY_INDIA ||
            packageName.startsWith("com.google.android.apps.nbu.paisa.")
    }

    private const val PACKAGE_INSTALLER = "com.android.packageinstaller"
    private const val GOOGLE_PACKAGE_INSTALLER = "com.google.android.packageinstaller"
    private const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"
    private const val GPAY_INDIA = "com.google.android.apps.nbu.paisa.user"
}
