package com.telefonica.androidsnaptesting

import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import com.android.build.gradle.internal.testing.ConnectedDevice
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.FileListingService
import com.android.ddmlib.FileListingService.FileEntry
import com.android.ddmlib.IDevice
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ProviderFactory
import java.io.File

fun DeviceProviderInstrumentTestTask.deviceFileManager(
    applicationId: String,
    adbExecutablePath: String,
    providerFactory: ProviderFactory,
): DeviceFileManager = DeviceFileManager(this, applicationId, adbExecutablePath, providerFactory)

class DeviceFileManager(
    private val testTask: DeviceProviderInstrumentTestTask,
    private val applicationId: String,
    private val adbExecutablePath: String,
    private val providerFactory: ProviderFactory,
) {

    fun pullRecordedSnapshots(
        destinationPath: String,
    ) {
        pullSnapshots("recorded", destinationPath)
    }

    fun pullFailuresSnapshots(
        destinationPath: String,
    ) {
        pullSnapshots("failures", destinationPath)
    }

    fun clearAllSnapshots() {
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
        "${FileListingService.DIRECTORY_SDCARD}/Download/android-snaptesting/$applicationId"
    private fun getDeviceAndroidSnaptestingSubfolderAbsolutePath(subFolder: String): String =
        "${getDeviceAndroidSnaptestingRootAbsolutePath()}/$subFolder"

    @Suppress("UnstableApiUsage")
    private fun withConnectedDevices(runnable: (List<ConnectedDevice>) -> Unit) {
        testTask.deviceProviderFactory.getDeviceProvider(
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
