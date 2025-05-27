package com.telefonica.loggerazzi

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    private val recorder = FakeTestRecorder()

    @get:Rule
    val loggerazziRule = LoggerazziRule(
        recorder = recorder
    )
    @get:Rule
    val screenshotsRule = ScreenshotsRule()

    @Test
    fun testSingleLog() {
        recorder.record("My log")
    }

    @Test
    fun testMultipleLogs() {
        recorder.record("My first log")
        recorder.record("My second log")
        recorder.record("My third log")
    }

    @Test
    fun testEmpty() {
        // Empty, just to test empty logs comparation.
    }

    @Test
    @IgnoreLogs
    fun testIgnoreLoggerazzi() {
        recorder.record("My log")
    }

    @Test
    @IgnoreLogs
    fun testIgnoreLoggerazziWithoutGoldenFile() {
        recorder.record("My log")
    }

    @Test
    @IgnoreLogs
    fun testLaunchActivity() {
        ActivityScenario.launch(MainActivity::class.java).onActivity {
            screenshotsRule.compareScreenshot(it, name = "launch_activity")
        }
    }
}

class FakeTestRecorder: LogsRecorder<String> {

    private val logs = mutableListOf<String>()

    fun record(log: String) {
        logs.add(log)
    }

    override fun getRecordedLogs(): List<String> {
        return logs
    }

    override fun clear() {
        logs.clear()
    }
}
