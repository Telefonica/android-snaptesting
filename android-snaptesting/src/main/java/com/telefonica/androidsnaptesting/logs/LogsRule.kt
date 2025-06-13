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

        val recordedLogs = recorder.getRecordedLogs()
        val log = recordedLogs.joinToString("\n") { stringMapper.fromLog(it) }
        val testFile = File(directories.recordedDir, fileName)
        testFile.createNewFile()
        testFile.writeText(log)

        if (InstrumentationRegistry.getArguments().getString("record") != "true" && !isTestIgnored) {
            val goldenFile = directories.context.assets.open("${directories.goldenFilesDir}/${testName}.txt")
            val goldenStringLogs = String(goldenFile.readBytes()).takeIf { it.isNotEmpty() }?.split("\n") ?: emptyList()
            val result = comparator.compare(recordedLogs, goldenStringLogs.map { stringMapper.toLog(it) })
            if (result != null) {
                val compareFile = File(directories.failuresDir, fileName)
                compareFile.createNewFile()
                compareFile.writeText(result)
                throw AssertionError("Logs do not match:\n$result")
            }
        }
    }
}
