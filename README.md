<p>
    <img src="https://img.shields.io/badge/Platform-Android-brightgreen" />
    <img src="https://img.shields.io/badge/Support-%3E%3D%20Android%206.0-brightgreen" />
</p>


# Android Snaptesting

Snapshot testing for Android Instrumentation tests: **Screenshots** and **Logs**.


## Features

- 📸 **Screenshot Testing**: Automatically compare UI screenshots to baselines to catch visual regressions.
- 📝 **Log Snapshot Testing**: Compare analytics or application logs to baselines to ensure correct events and order.

## Introduction

Android Snaptesting provides two powerful snapshot testing tools:

- **Screenshot Testing**: Ensures your UI remains visually consistent by comparing screenshots taken during tests to previously approved baselines.
- **Log Testing (Loggerazzi)**: Applies the snapshot testing approach to your analytics or application logs, verifying that the right events are logged in the correct order.


## Usage

### 1. Setup

Add the plugin to your project's `build.gradle`:

```gradle
plugins {
    ...
    id("com.telefonica.androidsnaptesting-plugin") version $android_snaptesting_version apply false
}
```

Then, apply it in your application or library module:

```gradle
plugins {
    ...
    id "com.telefonica.androidsnaptesting-plugin"
}
```

Add the dependency for instrumentation tests:

```gradle
dependencies {
    ...
    androidTestImplementation "com.telefonica:androidsnaptesting:$android_snaptesting_version"
}
```

### 2. Screenshot Testing

Add the screenshot rule to your test class (or base test class):

```kotlin
import com.telefonica.androidsnaptesting.screenshot.ScreenshotRule

open class BaseScreenshotTest {
    @get:Rule
    val screenshotRule = ScreenshotRule()
}

// In your test:
@Test
fun testMyScreen() {
    // ... launch your UI ...
    screenshotRule.snap("MyScreen_baseline")
}
```

This will compare the current screenshot to the baseline. If no baseline exists or you want to update it, see the Recording mode below.

### 3. Log Testing (Loggerazzi)

Add the Loggerazzi rule to your test class (or base instrumentation test class), providing a logs recorder (see Configuration):

```kotlin
import com.telefonica.loggerazzi.LoggerazziRule

open class BaseInstrumentationTest {
    @get:Rule
    val loggerazziRule: LoggerazziRule = LoggerazziRule(
        recorder = fakeAnalyticsTracker
    )
}

// In your test:
@Test
fun testMyEventLogging() {
    // ... trigger events ...
    // Loggerazzi will automatically verify logs at the end of the test
}
```

For more details, check the included [application example](app).


## Execution

### Verification mode

Run your instrumentation tests as usual to verify screenshots and logs against their baselines:

```bash
./gradlew :app:connectedDebugAndroidTest
```

If there are any failures (either screenshot or log mismatches), JUnit reports will include details, and specific HTML reports are generated:

- **Screenshot failures:** `build/reports/androidTests/connected/debug/screenshot/failures.html`
- **Loggerazzi (log) failures:** `build/reports/androidTests/connected/debug/loggerazzi/failures.html`

### Recording mode

To update baselines (screenshots or logs), add the record argument:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.record=true
```

This will generate new baselines for both screenshots and logs. Reports are generated at:

- **Screenshots:** `build/reports/androidTests/connected/debug/screenshot/recorded.html`
- **Logs:** `build/reports/androidTests/connected/debug/loggerazzi/recorded.html`

## Execution from external runners

In situations where the regular `connectedXXXXAndroidTest` target is not used because execution is performed by a different external test runner (such as composer or marathon), two loggerazzi gradle tasks are provided which should be executed manually before and after external test runner execution:
 - `loggerazziBefore[VariantName]AndroidTest`
 - `loggerazziAfter[VariantName]AndroidTest`

In case test execution is triggered from any gradle task, here's an example on how to configure dependencies with loggerazzi tasks:

```gradle
project.afterEvaluate {
    project.tasks.findByName("externalTestRunner[VariantName]Execution")
        .dependsOn("loggerazziBefore[VariantName]AndroidTest")
        .finalizedBy("loggerazziAfter[VariantName]AndroidTest")
}
```


## Configuration

### Screenshot Testing

The `ScreenshotRule` works out of the box, but you can customize:

- **Baseline directory**
- **Image comparison tolerance**
- **File naming**

Refer to the API docs or source for advanced configuration options.

### Logs recorder (Loggerazzi)

Loggerazzi rule must be configured with a [LogsRecorder](loggerazzi/src/main/java/com/telefonica/loggerazzi/LogsRecorder.kt) implementation, usually as a replacement for your analytics tracker in tests.

Example:

```kotlin
class FakeAnalyticsTracker : AnalyticsTracker, LogsRecorder<String> {
    private val logs = mutableListOf<String>()
    override fun clear() { logs.clear() }
    override fun getRecordedLogs(): List<String> = logs.mapIndexed { index, s -> "$index: $s" }
    override fun init() {}
    override fun trackScreenView(screen: AnalyticsScreen) { logs.add("trackScreenView: $screen") }
    override fun trackEvent(event: Event.GenericEvent) { logs.add("trackEvent: $event") }
}
```

#### Logs comparator

By default, Loggerazzi compares logs for exact match and order. For custom comparison (e.g., ignore order or certain logs), implement a [LogComparator](loggerazzi/src/main/java/com/telefonica/loggerazzi/LogComparator.kt) and provide it to the rule.

#### Ignore a test
To ignore a test from Loggerazzi verification, use the `@IgnoreLoggerazzi` annotation.

## Combining Screenshot and Log Testing

You can use both rules in the same test class to verify both UI and logs in a single test run:

```kotlin
import com.telefonica.androidsnaptesting.screenshot.ScreenshotRule
import com.telefonica.loggerazzi.LoggerazziRule

open class BaseUiAndLogTest {
    @get:Rule val screenshotRule = ScreenshotRule()
    @get:Rule val loggerazziRule = LoggerazziRule(recorder = fakeAnalyticsTracker)
}

@Test
fun testScreenAndLogs() {
    // ... launch UI and trigger events ...
    screenshotRule.snap("MyScreen_baseline")
    // Loggerazzi will verify logs automatically
}
```
