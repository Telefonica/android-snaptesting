package com.telefonica.androidsnaptesting

import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

public class LogsRule(
    recorder: LogsRecorder<String>,
    comparator: LogComparator<String> = DefaultLogComparator(),
) : GenericLogsRule<String>(
    recorder = recorder,
    stringMapper = object : StringMapper<String> {
        override fun fromLog(log: String): String = log
        override fun toLog(stringLog: String): String = stringLog
    },
    comparator = comparator,
)

public open class GenericLogsRule<LogType>(
    public val recorder: LogsRecorder<LogType>,
    private val stringMapper: StringMapper<LogType>,
    private val comparator: LogComparator<LogType> = DefaultLogComparator(),
) : TestWatcher() {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private val downloadDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    )
    private val androidSnaptestingDir = File(downloadDir, "android-snaptesting/${context.packageName}")
    private val failuresDir = File(androidSnaptestingDir, "failures")
    private val recordedDir = File(androidSnaptestingDir, "recorded")

    override fun starting(description: Description?) {
        super.starting(description)

        recorder.clear()
        if (!failuresDir.exists()) {
            failuresDir.mkdirs()
        }
        if (!recordedDir.exists()) {
            recordedDir.mkdirs()
        }
    }

    override fun succeeded(description: Description?) {
        super.succeeded(description)

        val isTestIgnored = description?.getAnnotation(IgnoreLogs::class.java) != null

        val testName = "${description?.className}_${description?.methodName}"
        val fileName = "${testName}.txt.${System.nanoTime()}"

        val recordedLogs = recorder.getRecordedLogs()
        val log = recordedLogs.joinToString("\n") { stringMapper.fromLog(it) }
        val testFile = File(recordedDir, fileName)
        testFile.createNewFile()
        testFile.writeText(log)

        if (InstrumentationRegistry.getArguments().getString("record") != "true" && !isTestIgnored) {
            val goldenFile =
                InstrumentationRegistry.getInstrumentation().context.assets.open(
                    "android-snaptesting-golden-files/${testName}.txt"
                )
            val goldenStringLogs = String(goldenFile.readBytes()).takeIf { it.isNotEmpty() }?.split("\n") ?: emptyList()
            val result = comparator.compare(recordedLogs, goldenStringLogs.map { stringMapper.toLog(it) })
            if (result != null) {
                val compareFile = File(failuresDir, fileName)
                compareFile.createNewFile()
                compareFile.writeText(result)
                throw AssertionError("Logs do not match:\n$result")
            }
        }
    }
}