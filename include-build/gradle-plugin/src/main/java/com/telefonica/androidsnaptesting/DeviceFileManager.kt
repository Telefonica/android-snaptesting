package com.telefonica.androidsnaptesting

import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.testing.ConnectedDevice
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ProviderFactory
import java.io.File
import java.util.concurrent.TimeUnit

fun DeviceProviderInstrumentTestTask.deviceFileManager(
    applicationId: String,
    adbExecutablePath: String,
    providerFactory: ProviderFactory,
): DeviceFileManager = DeviceFileManager(this.deviceProviderFactory, applicationId, adbExecutablePath, providerFactory)

class DeviceFileManager(
    private val deviceProviderFactory: DeviceProviderInstrumentTestTask.DeviceProviderFactory,
    private val applicationId: String,
    private val adbExecutablePath: String,
    private val providerFactory: ProviderFactory,
) {

    fun pullRecordedSnapshots(destinationPath: String) {
        pullSnapshots("recorded", destinationPath)
    }

    fun pullFailuresSnapshots(destinationPath: String) {
        pullSnapshots("failures", destinationPath)
    }

    fun clearAllSnapshots() {
        withConnectedDevices { devices ->
            devices.forEach { device ->
                runAdb(device.serialNumber, "shell", "rm", "-rf", getDeviceAndroidSnaptestingRootAbsolutePath())
            }
        }
    }

    private fun getDeviceAndroidSnaptestingRootAbsolutePath(): String =
        "/sdcard/Download/android-snaptesting/$applicationId"

    private fun getDeviceAndroidSnaptestingSubfolderAbsolutePath(subFolder: String): String =
        "${getDeviceAndroidSnaptestingRootAbsolutePath()}/$subFolder"

    @Suppress("UnstableApiUsage")
    private fun withConnectedDevices(runnable: (List<ConnectedDevice>) -> Unit) {
        deviceProviderFactory.getDeviceProvider(
            providerFactory.provider {
                RegularFile { File(adbExecutablePath) }
            },
            System.getenv("ANDROID_SERIAL"),
        ).let {
            it.use {
                runnable(it.devices.filterIsInstance<ConnectedDevice>())
            }
        }
    }

    private fun pullSnapshots(
        androidSnaptestingSubFolderInDevice: String,
        destinationPath: String,
    ) {
        val remotePath = getDeviceAndroidSnaptestingSubfolderAbsolutePath(androidSnaptestingSubFolderInDevice)
        withConnectedDevices { devices ->
            devices.forEach { device ->
                val serial = device.serialNumber
                val lsResult = runAdbCapture(serial, "shell", "ls", remotePath, logErrors = false)
                val fileNames = lsResult.output.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("ls:") && !it.contains("No such file") }
                fileNames.forEach { fileName ->
                    runAdb(serial, "pull", "$remotePath/$fileName", "$destinationPath/$fileName")
                }
            }
        }
    }

    private fun runAdb(serial: String, vararg args: String) {
        val result = runAdbCapture(serial, *args, throwOnError = true)
        println(result.output)
    }

    private fun runAdbCapture(
        serial: String,
        vararg args: String,
        throwOnError: Boolean = false,
        logErrors: Boolean = true,
    ): AdbResult {
        val command = buildList {
            add(adbExecutablePath)
            add("-s")
            add(serial)
            addAll(args.toList())
        }
        val result = try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val finished = process.waitFor(60, TimeUnit.SECONDS)
            val exitCode = process.exitValue()
            AdbResult(output, error, exitCode, finished)
        } catch (e: Exception) {
            val message = "Exception running ADB command: ${command.joinToString(" ")}\n${e.message}"
            if (throwOnError) throw RuntimeException(message, e)
            else if (logErrors) println(message)
            return AdbResult("", e.message ?: "", -1)
        }

        if (!result.finished || result.exitCode != 0) {
            val message = "ADB command failed: ${command.joinToString(" ")}\nExit code: ${result.exitCode}\nOutput: ${result.output}\nError: ${result.error}"
            if (throwOnError) throw RuntimeException(message)
            else if (logErrors) println(message)
        }
        return result
    }

    private data class AdbResult(val output: String, val error: String, val exitCode: Int, val finished: Boolean = true)
}
