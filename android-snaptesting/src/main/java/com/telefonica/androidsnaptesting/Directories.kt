package com.telefonica.androidsnaptesting

import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal class Directories {

    internal val context = InstrumentationRegistry.getInstrumentation().context
    private val downloadDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    )
    private val androidSnaptestingDir = File(downloadDir, "android-snaptesting/${context.packageName}")
    internal val failuresDir = File(androidSnaptestingDir, "failures")
    internal val recordedDir = File(androidSnaptestingDir, "recorded")
    internal val goldenFilesDir = "android-snaptesting-golden-files"
}
