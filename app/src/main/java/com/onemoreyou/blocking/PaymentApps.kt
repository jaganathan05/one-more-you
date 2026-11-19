package com.onemoreyou.blocking

object PaymentApps {
    fun isPayment(packageName: String): Boolean {
        return packageName == "com.google.android.apps.nbu.paisa.user" ||
            packageName == "com.google.android.apps.walletnfcrel" ||
            packageName.startsWith("com.google.android.apps.nbu.paisa.")
    }
}
