package com.telefonica.androidsnaptesting

import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.ProviderFactory
import java.io.File

class AndroidSnaptestingPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.afterEvaluate {

            val deviceProviderInstrumentTestTasks = project.tasks
                .withType(DeviceProviderInstrumentTestTask::class.java)

            if (deviceProviderInstrumentTestTasks.isEmpty()) {
                throw AndroidSnaptestingNoDeviceProviderInstrumentTestTasksException()
            }

            val extension = project.extensions.findByType(TestedExtension::class.java)
                ?: throw RuntimeException("TestedExtension not found")

            val isRecordMode = project.properties["android.testInstrumentationRunnerArguments.record"] == "true"
            val projectDir = project.projectDir
            val providerFactory: ProviderFactory = project.providers

            deviceProviderInstrumentTestTasks.names.forEach { taskName ->
                val deviceProviderTask = project.tasks.named(
                    taskName,
                    DeviceProviderInstrumentTestTask::class.java,
                ).get()
                val capitalizedVariant = deviceProviderTask.variantName.capitalizeFirstLetter()

                @Suppress("DEPRECATION")
                val testedVariant = extension.testVariants
                    .firstOrNull { it.name == deviceProviderTask.variantName }
                    ?: throw RuntimeException("TestVariant not found for ${deviceProviderTask.variantName}")
                val applicationIdProvider = providerFactory.provider { testedVariant.applicationId }
                val adbExecutablePath = extension.adbExecutable.absolutePath

                val goldenSnapshotsSourcePath = run {
                    val variantSourceFolder = deviceProviderTask
                        .variantName
                        .replace("AndroidTest", "")
                        .capitalizeFirstLetter()
                        .let { "androidTest$it" }
                    "$projectDir/src/$variantSourceFolder/assets/android-snaptesting-golden-files"
                }

                // Attach work directly on the deviceProviderTask (config-cache safe).
                // Note: in Kotlin, doFirst/doLast lambdas receive the task as 'it', not 'this'.
                deviceProviderTask.doFirst {
                    (it as DeviceProviderInstrumentTestTask)
                        .deviceFileManager(applicationIdProvider.get(), adbExecutablePath, providerFactory)
                        .clearAllSnapshots()
                }

                deviceProviderTask.doLast {
                    (it as DeviceProviderInstrumentTestTask)
                        .afterExecution(
                            applicationId = applicationIdProvider.get(),
                            adbExecutablePath = adbExecutablePath,
                            providerFactory = providerFactory,
                            isRecordMode = isRecordMode,
                            goldenSnapshotsSourcePath = goldenSnapshotsSourcePath,
                        )
                }

                // Keep empty before/after tasks as dependency anchors for CI scripts
                // (e.g. ci.gradle.kts references these task names).
                val beforeTaskName = "androidSnaptestingBefore$capitalizedVariant"
                project.tasks.register(beforeTaskName, Task::class.java)
                deviceProviderTask.dependsOn(beforeTaskName)

                val afterTaskName = "androidSnaptestingAfter$capitalizedVariant"
                project.tasks.register(afterTaskName, Task::class.java)
                deviceProviderTask.finalizedBy(afterTaskName)
            }
        }
    }

    private fun DeviceProviderInstrumentTestTask.afterExecution(
        applicationId: String,
        adbExecutablePath: String,
        providerFactory: ProviderFactory,
        isRecordMode: Boolean,
        goldenSnapshotsSourcePath: String,
    ) {
        val deviceFileManager = deviceFileManager(applicationId, adbExecutablePath, providerFactory)

        val reportsFolder = reportsDir.get().dir("androidSnaptesting")
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
