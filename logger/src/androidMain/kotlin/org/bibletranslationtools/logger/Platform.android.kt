package org.bibletranslationtools.logger

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import kotlin.system.exitProcess

class AndroidConsole : PlatformConsole {
    override fun print(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        when (level) {
            LogLevel.Info -> Log.i(tag, message)
            LogLevel.Warning -> Log.w(tag, message, throwable)
            LogLevel.Error -> Log.e(tag, message, throwable)
        }
    }
}

actual typealias PlatformContext = Context

actual fun getEnvironmentBlock(context: PlatformContext): String = buildString {
    append("\nEnvironment\n======\n")
    append("Environment Key | Value\n")
    append(":----: | :----:\n")

    val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }

    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        pInfo.versionCode.toLong()
    }
    append("version | ${pInfo.versionName}\n")
    append("build | $versionCode\n")

    @SuppressLint("HardwareIds")
    val udid = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    append("UDID | $udid\n")
    append("Android Release | ${Build.VERSION.RELEASE}\n")
    append("Android SDK | ${Build.VERSION.SDK_INT}\n")
    append("Brand | ${Build.BRAND}\n")
    append("Device | ${Build.DEVICE}\n")
    append("Model | ${Build.MODEL}\n")
}

actual fun getDefaultConsole(): PlatformConsole = AndroidConsole()
actual fun killProcess() {
    Process.killProcess(Process.myPid())
    exitProcess(0)
}