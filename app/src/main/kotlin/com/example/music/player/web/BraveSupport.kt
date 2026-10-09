package com.example.music.player.web

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent

object BraveSupport {
    val PACKAGES = listOf(
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.brave.browser_nightly",
    )

    fun installedPackage(context: Context): String? {
        val pm = context.packageManager
        return PACKAGES.firstOrNull { pkg ->
            runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                true
            }.getOrDefault(false)
        }
    }

    fun playStoreIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.brave.browser")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openBridge(context: Context, url: String, preferBrave: Boolean) {
        val uri = Uri.parse(url)
        val brave = if (preferBrave) installedPackage(context) else null
        if (brave != null) {
            val ok = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage(brave)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                true
            }.getOrDefault(false)
            if (ok) return
            val launched = runCatching {
                val tabs = CustomTabsIntent.Builder().build()
                tabs.intent.setPackage(brave)
                tabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                tabs.launchUrl(context, uri)
                true
            }.getOrDefault(false)
            if (launched) return
        }
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
