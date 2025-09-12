<p>
    <img src="https://img.shields.io/badge/Platform-Android-brightgreen" />
    <img src="https://img.shields.io/badge/Support-%3E%3D%20Android%206.0-brightgreen" />
</p>

# Android Snaptesting

Logs and screenshots snapshot testing for Android Instrumentation tests.

## Introduction

Android Snaptesting provides two powerful snapshot testing approaches for Android:

1. **📸 Screenshot Testing**: Captures and compares UI snapshots to ensure your application's visual appearance doesn't break unexpectedly.
2. **📝 Logs Testing**: Captures and compares analytics events or any application logs to ensure your tracking implementation remains consistent.

Both approaches use the same "snapshoting" concept - record a baseline once, then verify against it in future test runs to catch regressions.

## Usage

You just need to include the Android Snaptesting plugin in your project, and the appropriate rules in your test class (configuring them properly).

In order to universally include all your existing application tests, rules can be added to your test base class.

### Setup

To include the plugin, add it to the plugins block of your project's build.gradle:

```gradle
plugins {
    ...
    id("com.telefonica.androidsnaptesting-plugin") version $android_snaptesting_version apply false
}
```
Then, include it in your specific application or library build.gradle:
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

Add the `ScreenshotsRule` to your test class:

```kotlin
open class BaseInstrumentationTest {
    @get:Rule
    val screenshotsRule: ScreenshotsRule = ScreenshotsRule()
}
```

Then use it in your tests:

```kotlin
@Test
fun verifyScreenAppearance() {
    // Navigate to screen or setup activity
    screenshotsRule.compareScreenshot(activity, "screen_name")
}
```

### Logs Testing

Add the `LogsRule` to your test class (or base instrumentation tests class), where a logs recorder must be provided (check the configuration section):

```kotlin
open class BaseInstrumentationTest {
    @get:Rule
    val logsRule: LogsRule = LogsRule(
        recorder = fakeAnalyticsTracker
    )
}
```

For more details, check the included [application example](app).

## Execution

### Verification mode

Regular `connectedXXXXAndroidTest` target invocation is enough for verifications against previously generated baselines (both screenshots and logs). Android Studio executions should also work seamlessly.

```bash
./gradlew :app:connectedDebugAndroidTest
```

In case of any failures due to screenshots or log verifications, regular JUnit reports include failed tests and the comparison failure reason.

Additionally, a specific report is generated at --> `build/reports/androidTests/connected/debug/androidSnaptesting/failures.html`

### Recording mode

When the baselines need to be updated, it's enough to include `-Pandroid.testInstrumentationRunnerArguments.record=true`.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.record=true
```

This execution won't perform any verification; instead, it will execute tests to generate new baselines, placing them in the corresponding test baseline directory.

Reports with all recorded items are generated at --> `build/reports/androidTests/connected/debug/androidSnaptesting/recorded.html`

## Execution from external runners

In situations where the regular `connectedXXXXAndroidTest` target is not used because execution is performed by a different external test runner (such as Composer or Marathon), two sets of Gradle tasks are provided, which should be executed manually before and after external test runner execution:
 - `androidSnaptestingBefore[VariantName]AndroidTest` and `androidSnaptestingAfter[VariantName]AndroidTest`

In case test execution is triggered from any Gradle task, here's an example on how to configure dependencies with these tasks:

```gradle
project.afterEvaluate {
    project.tasks.findByName("externalTestRunner[VariantName]Execution")
        .dependsOn("androidSnaptestingBefore[VariantName]AndroidTest")
        .finalizedBy("androidSnaptestingAfter[VariantName]AndroidTest")
}
```

## Configuration

### Screenshot Configuration

The `ScreenshotsRule` can be configured with several options:

- `ImageComparator`: by default, it uses a `SimpleImageComparator` that compares the pixels, but you can implement a different one. You can configure the `maxDistance` in `SimpleImageComparator`.
- `ResultValidator`, there are two implementations available, although you can provide a different one: 
  - `CountValidator`: accepts or rejects image comparison results based on the absolute number of pixel differences. It's used by default.
  - `ThresholdValidator`: accepts or rejects image comparison results based on a percentage of pixel differences rather than an absolute count.

```kotlin
val screenshotRule = ScreenshotsRule(
    imageComparator = SimpleImageComparator(maxDistance = 0.004f),
    resultValidator = ThresholdValidator(0.9),
)
```

### Logs recorder

`LogsRule` must be configured with a [LogsRecorder](android-snaptesting/src/main/java/com/telefonica/androidsnaptesting/logs/LogsRecorder.kt) implementation, which will be used by LogsRule to obtain logs recorded at the end of the test. This should usually be implemented as the replacement of the original application tracker in tests.

Example:

```kotlin
class FakeAnalyticsTracker : AnalyticsTracker, LogsRecorder<String> {

    private val logs = mutableListOf<String>()

    override fun clear() {
        logs.clear()
    }

    override fun getRecordedLogs(): List<String> =
        logs.mapIndexed { index, s ->
            "$index: $s"
        }

    override fun init() {}

    override fun trackScreenView(screen: AnalyticsScreen) {
        logs.add("trackScreenView: $screen")
    }

    override fun trackEvent(event: Event.GenericEvent) {
        logs.add("trackEvent: $event")
    }
}
```

### Logs comparator

By default, `LogsRule` compares recorded logs by ensuring these are equal and in the same order as the baseline logs.

In case a different comparison mechanism is needed (such as ignoring the order of the events, or ignoring certain logs), you can implement a specific [LogComparator](android-snaptesting/src/main/java/com/telefonica/androidsnaptesting/logs/LogComparator.kt), which can be provided to the `LogsRule` on its creation.

## Ignore a test
If you want to ignore a test from logs or screenshots verification, you can use these annotations in your tests:

- Ignore logs verification -> `@IgnoreLogs`
- Ignore screenshots verification -> `@IgnoreScreenshots`
