package com.telefonica.androidsnaptesting

import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.testing.ConnectedDevice
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.FileListingService
import com.android.ddmlib.FileListingService.FileEntry
import com.android.ddmlib.IDevice
import org.gradle.api.file.RegularFile
import java.io.File

fun DeviceProviderInstrumentTestTask.deviceFileManager(): DeviceFileManager =
    DeviceFileManager(this)

class DeviceFileManager(
    private val testTask: DeviceProviderInstrumentTestTask,
) {
    private val extension: TestedExtension = testTask
        .project
        .extensions
        .findByType(TestedExtension::class.java)
        ?: throw RuntimeException("TestedExtension not found")

    @Suppress("DEPRECATION")
    private val testedVariant: TestVariant = extension
        .testVariants
        .firstOrNull { it.name == testTask.variantName }
        ?: throw RuntimeException("TestVariant not found")

    fun pullRecordedLogs(
        destinationPath: String,
    ) {
        pullLogs("recorded", destinationPath)
    }

    fun pullFailuresLogs(
        destinationPath: String,
    ) {
        pullLogs("failures", destinationPath)
    }

    fun clearAllLogs() {
        withConnectedDevices { devices ->
            devices.forEach {
                val receiver = CollectingOutputReceiver()
                it.iDevice.executeShellCommand("rm -rf ${getDeviceAndroidSnaptestingRootAbsolutePath()}", receiver)
                println(receiver.output)
            }
        }
    }

    private fun String.toFileEntry(): FileEntry {
        val parts = this.split("/")
        var fileEntry = FileEntry(null, null, FileListingService.TYPE_DIRECTORY, true)
        parts.forEach {
            fileEntry = FileEntry(fileEntry, it, FileListingService.TYPE_DIRECTORY, false)
        }
        return fileEntry
    }

    private fun getDeviceAndroidSnaptestingRootAbsolutePath(): String =
        "${FileListingService.DIRECTORY_SDCARD}/Download/android-snaptesting/${testedVariant.applicationId}"
    private fun getDeviceAndroidSnaptestingSubfolderAbsolutePath(subFolder: String): String =
        "${getDeviceAndroidSnaptestingRootAbsolutePath()}/$subFolder"

    @Suppress("UnstableApiUsage")
    private fun withConnectedDevices(runnable: (List<ConnectedDevice>) -> Unit) {
        testTask.deviceProviderFactory.getDeviceProvider(
            testTask.project.provider {
                RegularFile { File(extension.adbExecutable.absolutePath) }
            },
            System.getenv("ANDROID_SERIAL"),
        ).let {
            it.use {
                runnable(it.devices.filterIsInstance<ConnectedDevice>())
            }
        }
    }

    private fun pullLogs(
        androidSnaptestingSubFolderInDevice: String,
        destinationPath: String,
    ) {
        val fileEntry = getDeviceAndroidSnaptestingSubfolderAbsolutePath(androidSnaptestingSubFolderInDevice).toFileEntry()
        withConnectedDevices { devices ->
            devices.forEach {
                pullFolderFiles(
                    fileEntry,
                    it.iDevice,
                    destinationPath,
                )
            }
        }
    }

    private fun pullFolderFiles(
        androidSnaptestingDeviceFolder: FileEntry,
        device: IDevice,
        destinationPath: String,
    ) {
        device.fileListingService.getChildrenSync(androidSnaptestingDeviceFolder).forEach {
            device.pullFile(it.fullPath, "$destinationPath/${it.name}")
        }
    }
}