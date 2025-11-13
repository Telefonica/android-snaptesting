package com.telefonica.androidsnaptesting.logs

import androidx.test.platform.app.InstrumentationRegistry
import com.telefonica.androidsnaptesting.Directories
import com.telefonica.androidsnaptesting.IgnoreLogs
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

    private val directories = Directories()

    override fun starting(description: Description?) {
        super.starting(description)

        recorder.clear()
        if (!directories.failuresDir.exists()) {
            directories.failuresDir.mkdirs()
        }
        if (!directories.recordedDir.exists()) {
            directories.recordedDir.mkdirs()
        }
    }

    override fun succeeded(description: Description?) {
        super.succeeded(description)

        val isTestIgnored = description?.getAnnotation(IgnoreLogs::class.java) != null

        val testName = "${description?.className}_${description?.methodName}"
        val fileName = "${testName}.txt.${System.nanoTime()}"

        val recordedLogs: List<LogType>

        if (InstrumentationRegistry.getArguments().getString("record") != "true" && !isTestIgnored) {
            val goldenFile = directories.context.assets.open("${directories.goldenFilesDir}/${testName}.txt")
            val goldenStringLogs = String(goldenFile.readBytes()).takeIf { it.isNotEmpty() }?.split("\n") ?: emptyList()
            val comparison = compare(goldenStringLogs)
            if (!comparison.success) {
                val compareFile = File(directories.failuresDir, fileName)
                compareFile.createNewFile()
                compareFile.writeText(comparison.failure!!)
                throw AssertionError("Logs do not match:\n${comparison.failure}")
            }
            recordedLogs = comparison.recordedLogs
        } else {
            recordedLogs = recorder.getRecordedLogs()
        }

        val log = recordedLogs.joinToString("\n") { stringMapper.fromLog(it) }
        val testFile = File(directories.recordedDir, fileName)
        testFile.createNewFile()
        testFile.writeText(log)
    }

    private fun compare(goldenStringLogs: List<String>): Comparison<LogType> {
        val goldenLogs = goldenStringLogs.map { stringMapper.toLog(it) }
        val startTime = System.currentTimeMillis()
        var comparison: Comparison<LogType>
        do {
            val recordedLogs = recorder.getRecordedLogs()
            val comparisonFailure = comparator.compare(recordedLogs, goldenLogs)
            comparison = Comparison(comparisonFailure, recordedLogs)
            if (!comparison.success) {
                Thread.sleep(RESULT_POLLING_INTERVAL_MS)
            }
        } while (!comparison.success && System.currentTimeMillis() - startTime < RESULT_TIMEOUT_MS)
        return comparison
    }

    private data class Comparison<LogType>(
        val failure: String?,
        val recordedLogs: List<LogType>,
    ) {
        val success: Boolean
            get() = failure == null
    }

    private companion object {
        const val RESULT_POLLING_INTERVAL_MS = 500L
        const val RESULT_TIMEOUT_MS = 5000L
    }
}
