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
                // List files in the remote folder; ignore errors if the folder doesn't exist yet
                val lsResult = runAdbCapture(serial, "shell", "ls", remotePath)
                val fileNames = lsResult.output.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("ls:") && !it.contains("No such file") }
                // Pull each file to the local destination
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
    ): AdbResult {
        val command = buildList {
            add(adbExecutablePath)
            add("-s")
            add(serial)
            addAll(args.toList())
        }
        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val finished = process.waitFor(60, TimeUnit.SECONDS)
            val exitCode = process.exitValue()
            if (!finished || exitCode != 0) {
                val message = "ADB command failed: ${command.joinToString(" ")}\nExit code: $exitCode\nOutput: $output\nError: $error"
                if (throwOnError) throw RuntimeException(message)
                else println(message)
            }
            return AdbResult(output, error, exitCode)
        } catch (e: Exception) {
            val message = "Exception running ADB command: ${command.joinToString(" ")}\n${e.message}"
            if (throwOnError) throw RuntimeException(message, e)
            else println(message)
            return AdbResult("", e.message ?: "", -1)
        }
    }

    private data class AdbResult(val output: String, val error: String, val exitCode: Int)
}
