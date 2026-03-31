package com.telefonica.androidsnaptesting

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.io.File

class AndroidSnaptestingPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Collect applicationId per test-variant name at configuration time using the new variant API.
        // onVariants runs during project configuration, before afterEvaluate.
        val applicationIds = mutableMapOf<String, Provider<String>>()

        project.extensions.findByType(ApplicationAndroidComponentsExtension::class.java)
            ?.onVariants { variant ->
                applicationIds["${variant.name}AndroidTest"] = variant.applicationId
            }

        project.afterEvaluate {

            val deviceProviderInstrumentTestTasks = project.tasks
                .withType(DeviceProviderInstrumentTestTask::class.java)

            if (deviceProviderInstrumentTestTasks.isEmpty()) {
                throw AndroidSnaptestingNoDeviceProviderInstrumentTestTasksException()
            }

            val androidComponents = project.extensions.findByType(AndroidComponentsExtension::class.java)
                ?: throw RuntimeException("AndroidComponentsExtension not found")

            val isRecordMode = project.properties["android.testInstrumentationRunnerArguments.record"] == "true"
            val providerFactory: ProviderFactory = project.providers

            deviceProviderInstrumentTestTasks.names.forEach { taskName ->
                val deviceProviderTask = project.tasks.named(
                    taskName,
                    DeviceProviderInstrumentTestTask::class.java,
                ).get()
                val variantName = deviceProviderTask.variantName
                val applicationIdProvider = applicationIds[variantName]
                    ?: throw RuntimeException(
                        "applicationId not found for test variant '$variantName'. " +
                            "Available variants: ${applicationIds.keys}. " +
                            "Make sure the plugin is applied to a com.android.application module."
                    )
                registerTasksForVariant(
                    project, taskName, deviceProviderTask,
                    androidComponents, applicationIdProvider,
                    isRecordMode, providerFactory,
                )
            }
        }
    }

    private fun registerTasksForVariant(
        project: Project,
        taskName: String,
        deviceProviderTask: DeviceProviderInstrumentTestTask,
        androidComponents: AndroidComponentsExtension<*, *, *>,
        applicationIdProvider: Provider<String>,
        isRecordMode: Boolean,
        providerFactory: ProviderFactory,
    ) {
        val capitalizedVariant = deviceProviderTask.variantName.capitalizeFirstLetter()
        val adbExecutablePath = androidComponents.sdkComponents.adb.get().asFile.absolutePath

        val goldenSnapshotsSourcePath = run {
            val variantSourceFolder = deviceProviderTask
                .variantName
                .replace("AndroidTest", "")
                .capitalizeFirstLetter()
                .let { "androidTest$it" }
            "${project.projectDir}/src/$variantSourceFolder/assets/android-snaptesting-golden-files"
        }

        // Shared provider — used by both before and after tasks (config-cache safe: references task by name)
        val deviceProviderFactoryProvider = project.tasks.named(taskName, DeviceProviderInstrumentTestTask::class.java)
            .map { it.deviceProviderFactory }

        // Before task clears snapshots and serves as dependency anchor for CI scripts.
        val beforeTaskName = "androidSnaptestingBefore$capitalizedVariant"
        project.tasks.register(beforeTaskName, Task::class.java) { task ->
            task.doFirst {
                DeviceFileManager(deviceProviderFactoryProvider.get(), applicationIdProvider.get(), adbExecutablePath, providerFactory)
                    .clearAllSnapshots()
            }
        }
        deviceProviderTask.dependsOn(beforeTaskName)

        // After task runs post-processing via finalizedBy, which guarantees
        // execution even when the test task fails (needed to pull snapshot
        // results and generate reports on failure).
        val afterTaskName = "androidSnaptestingAfter$capitalizedVariant"
        val reportsDirProvider = project.tasks.named(taskName, DeviceProviderInstrumentTestTask::class.java)
            .flatMap { it.reportsDir }

        project.tasks.register(afterTaskName, Task::class.java) { task ->
            task.doLast {
                afterExecution(
                    deviceProviderFactory = deviceProviderFactoryProvider.get(),
                    reportsDir = reportsDirProvider.get(),
                    applicationId = applicationIdProvider.get(),
                    adbExecutablePath = adbExecutablePath,
                    providerFactory = providerFactory,
                    isRecordMode = isRecordMode,
                    goldenSnapshotsSourcePath = goldenSnapshotsSourcePath,
                )
            }
        }
        deviceProviderTask.finalizedBy(afterTaskName)
    }

    private fun afterExecution(
        deviceProviderFactory: DeviceProviderInstrumentTestTask.DeviceProviderFactory,
        reportsDir: Directory,
        applicationId: String,
        adbExecutablePath: String,
        providerFactory: ProviderFactory,
        isRecordMode: Boolean,
        goldenSnapshotsSourcePath: String,
    ) {
        val deviceFileManager = DeviceFileManager(deviceProviderFactory, applicationId, adbExecutablePath, providerFactory)

        val reportsFolder = reportsDir.dir("androidSnaptesting")
        val recordedFolderFile = reportsFolder.dir("recorded").asFile.apply {
            mkdirs()
            deviceFileManager.pullRecordedSnapshots(absolutePath)
        }
        val failuresFolderFile = reportsFolder.dir("failures").asFile.apply {
            mkdirs()
            deviceFileManager.pullFailuresSnapshots(absolutePath)
        }
        filterRecordedAndFailureResults(recordedFolderFile, failuresFolderFile)
        val goldenForFailuresReportFolderFile = reportsFolder.dir("golden").asFile.apply {
            mkdirs()
        }
        val goldenFolderFile = File(goldenSnapshotsSourcePath)

        File("${reportsFolder.asFile.absolutePath}/recorded.html").apply {
            createNewFile()
            val recordedFiles = recordedFolderFile.listFiles()?.asList() ?: emptyList()
            val report = AndroidSnaptestingReportConst.reportHtml.replace(
                oldValue = "REPORT_TEMPLATE_BODY",
                newValue = getRecordedReport(recordedFiles, reportsFolder.asFile)
            )
            writeText(report)
        }

        if (!isRecordMode) {
            File("${reportsFolder.asFile.absolutePath}/failures.html").apply {
                createNewFile()
                val failuresFiles = failuresFolderFile.listFiles()?.asList() ?: emptyList()
                val failuresEntries = failuresFiles.map { failureFile ->
                    FailureEntry(
                        failure = failureFile,
                        recorded = File(recordedFolderFile, failureFile.name),
                        golden = File(goldenFolderFile, failureFile.name).let {
                            it.copyTo(
                                File(goldenForFailuresReportFolderFile, it.name),
                                true
                            )
                        }
                    )
                }
                val report = AndroidSnaptestingReportConst.reportHtml.replace(
                    oldValue = "REPORT_TEMPLATE_BODY",
                    newValue = getFailuresReport(failuresEntries, reportsFolder.asFile)
                )
                writeText(report)
            }
        } else {
            File(goldenSnapshotsSourcePath).apply {
                mkdirs()
                recordedFolderFile.copyRecursively(this, true)
            }
        }
    }

    private fun String.capitalizeFirstLetter(): String {
        return replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun filterRecordedAndFailureResults(recordedDir: File, failuresDir: File) {
        recordedDir.listFiles()
            ?.groupBy {
                it.name.substringBeforeLast(".")
            }
            ?.forEach { (key, filesGroup) ->
                val lastRecordedFile = filesGroup.maxByOrNull {
                    it.name.substringAfterLast(".").toLong()
                }
                filesGroup.forEach { file ->
                    if (file != lastRecordedFile) {
                        file.delete()
                        File(failuresDir, file.name).takeIf { it.exists() }?.delete()
                    }
                }
                if (lastRecordedFile != null) {
                    lastRecordedFile
                        .renameTo(File(recordedDir, key))
                    File(failuresDir, lastRecordedFile.name)
                        .takeIf { it.exists() }
                        ?.renameTo(File(failuresDir, key))
                }
            }
    }
}
