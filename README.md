<p>
    <img src="https://img.shields.io/badge/Platform-Android-brightgreen" />
    <img src="https://img.shields.io/badge/Support-%3E%3D%20Android%206.0-brightgreen" />
</p>

# Android Snaptesting

Logs and screenshots snapshot testing for Android Instrumentation tests.

## Introduction

Android Snaptesting provides two powerful snapshot testing approaches for Android:

1. **Screenshot Testing**: Captures and compares UI snapshots to ensure your application's visual appearance doesn't break unexpectedly.
2. **Logs Testing (Loggerazzi)**: Captures and compares analytics events or any application logs to ensure your tracking implementation remains consistent.

Both approaches use the same "snapshoting" concept - record a baseline once, then verify against it in future test runs to catch regressions.

## Usage

You just need to include the Android Snaptesting plugin in your project, and the appropriate rules in your test class (configuring them properly).

In order to universally include all your existing application tests, rules can be added to your tests base class.

### Setup

To include the plugin, add it to the plugins block of your project's build.gradle:

```gradle
plugins {
    ...
    id("com.telefonica.androidsnaptesting-plugin") version $android_snaptesting_version apply false
}
```
Then, include it into your specific application or library build.gradle:
```gradle
plugins {
    ...
    id "com.telefonica.androidsnaptesting-plugin"
}
```

Also, include the rule dependency in your application or library dependencies block:

```gradle
dependencies {
    ...
    androidTestImplementation "com.telefonica:androidsnaptesting:$android_snaptesting_version"
}
```

### Screenshot Testing

Add the ScreenshotRule to your test class:

```kotlin
open class BaseInstrumentationTest {
    @get:Rule
    val screenshotRule: ScreenshotRule = ScreenshotRule()
}
```

Then use it in your tests:

```kotlin
@Test
fun verifyScreenAppearance() {
    // Navigate to screen or setup view
    screenshotRule.assertScreenshot(view, "screen_name")
}
```

### Logs Testing (Loggerazzi)

Add Loggerazzi rule to your test class (or base instrumentation tests class), where a logs recorder must be provided (Check configuration section):

```kotlin
open class BaseInstrumentationTest {
    @get:Rule
    val loggerazziRule: LoggerazziRule = LoggerazziRule(
        recorder = fakeAnalyticsTracker
    )
}
```

For more details, check included [application example](app).

## Execution

### Verification mode

Regular `connectedXXXXAndroidTest` target invocation is enough for verifications against previously generated baselines (both screenshots and logs). Android Studio executions should also work seamlessly.

```bash
./gradlew :app:connectedDebugAndroidTest
```

In case of any failures:
- For logs verifications, regular junit reports include failed tests and comparison failure reason.
- For screenshot verifications, a report with visual differences is generated.

Additionally:
- An specific Loggerazzi report is generated at --> `build/reports/androidTests/connected/debug/loggerazzi/failures.html`
- A screenshot comparison report is generated at --> `build/reports/androidTests/connected/debug/screenshots/failures.html`

### Recording mode

When the baselines need to be updated, it's enough to include `-Pandroid.testInstrumentationRunnerArguments.record=true`.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.record=true
```

This execution won't perform any verification, instead, it will execute tests to generate new baselines, placing them in the corresponding tests baseline directory.

Reports with all recorded items are generated at:
- Logs: `build/reports/androidTests/connected/debug/loggerazzi/recorded.html`
- Screenshots: `build/reports/androidTests/connected/debug/screenshots/recorded.html`

## Execution from external runners

In situations where the regular `connectedXXXXAndroidTest` target is not used because execution is performed by a different external test runner (such as composer or marathon), two sets of gradle tasks are provided which should be executed manually before and after external test runner execution:
 - `loggerazziBefore[VariantName]AndroidTest` and `loggerazziAfter[VariantName]AndroidTest`
 - `screenshotBefore[VariantName]AndroidTest` and `screenshotAfter[VariantName]AndroidTest`

In case test execution is triggered from any gradle task, here's an example on how to configure dependencies with these tasks:

```gradle
project.afterEvaluate {
    project.tasks.findByName("externalTestRunner[VariantName]Execution")
        .dependsOn("loggerazziBefore[VariantName]AndroidTest", "screenshotBefore[VariantName]AndroidTest")
        .finalizedBy("loggerazziAfter[VariantName]AndroidTest", "screenshotAfter[VariantName]AndroidTest")
}
```

## Configuration

### Screenshot Configuration

The ScreenshotRule can be configured with several options:

```kotlin
val screenshotRule = ScreenshotRule(
    tolerance = 0.01, // 1% difference allowed
    comparator = CustomScreenshotComparator(), // Custom comparison logic
    screenshotDirectory = "custom_directory" // Custom directory for baselines
)
```

### Logs Recorder

Loggerazzi rule must be configured with a [LogsRecorder](loggerazi
