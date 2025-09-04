package com.telefonica.androidsnaptesting.screenshots

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.runner.screenshot.Screenshot
import com.dropbox.differ.ImageComparator
import com.dropbox.differ.Mask
import com.dropbox.differ.SimpleImageComparator
import com.telefonica.androidsnaptesting.Directories
import com.telefonica.androidsnaptesting.IgnoreScreenshots
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.io.FileNotFoundException

public class ScreenshotsRule(
    private val imageComparator: ImageComparator = SimpleImageComparator(maxDistance = 0.004f),
    private val resultValidator: ResultValidator = CountValidator(0),
) : TestRule {

    private var className: String = ""
    private var testName: String = ""
    private var isTestIgnored: Boolean = false
    private val writeDiffImage = WriteDiffImage()

    private val directories = Directories()

    override fun apply(base: Statement, description: Description): Statement {
        className = description.className
        testName = description.methodName
        isTestIgnored = description.getAnnotation(IgnoreScreenshots::class.java) != null

        if (!directories.failuresDir.exists()) {
            directories.failuresDir.mkdirs()
        }
        if (!directories.recordedDir.exists()) {
            directories.recordedDir.mkdirs()
        }
        return base
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public fun compareScreenshot(
        rule: ComposeTestRule,
        name: String? = null,
    ) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        compareScreenshot(bitmap, name)
    }

    public fun compareScreenshot(
        activity: Activity,
        name: String? = null,
    ) {
        val view = activity.findViewById<View>(android.R.id.content)

        val bitmap = Screenshot.capture(activity).bitmap
        compareScreenshot(bitmap, name, view)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    public fun compareScreenshot(
        bitmap: Bitmap,
        name: String? = null,
        view: View? = null,
    ) {
        disableFlakyComponentsAndWaitForIdle(view)
        val resourceName = "${className}_${name ?: testName}.png"
        val fileName = "$resourceName.${System.nanoTime()}"
        saveScreenshot(fileName, bitmap)

        if (InstrumentationRegistry.getArguments().getString("record") != "true" && !isTestIgnored) {
            val goldenBitmap = getGoldenBitmap(resourceName)
            compareImagesSize(bitmap, goldenBitmap, fileName, name)
            compareImages(bitmap, goldenBitmap, fileName, resourceName)
        }
    }

    private fun saveScreenshot(fileName: String, bitmap: Bitmap) {
        val testFile = File(directories.recordedDir, fileName)
        testFile.createNewFile()
        testFile.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun getGoldenBitmap(resourceName: String): Bitmap {
        val goldenBitmap = try {
            directories.context.assets.open("${directories.goldenFilesDir}/$resourceName").use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: FileNotFoundException) {
            throw IllegalStateException(
                "Failed to find golden image named $resourceName. If this is a new test, you may need to record screenshots",
                e
            )
        }
        return goldenBitmap
    }

    private fun compareImagesSize(
        bitmap: Bitmap,
        goldenBitmap: Bitmap,
        fileName: String,
        name: String?
    ) {
        if (bitmap.width != goldenBitmap.width || bitmap.height != goldenBitmap.height) {
            writeDiffImage(directories.failuresDir, fileName, bitmap, goldenBitmap, null)
            throw AssertionError(
                "$name: Test image (w=${bitmap.width}, h=${bitmap.height}) differs in size" +
                        " from reference image (w=${goldenBitmap.width}, h=${goldenBitmap.height}).\n",
            )
        }
    }

    private fun compareImages(
        bitmap: Bitmap,
        goldenBitmap: Bitmap,
        fileName: String,
        resourceName: String
    ) {
        val mask = Mask(bitmap.width, bitmap.height)
        val result = try {
            imageComparator.compare(BitmapImage(goldenBitmap), BitmapImage(bitmap), mask)
        } catch (e: IllegalArgumentException) {
            writeDiffImage(directories.failuresDir, fileName, bitmap, goldenBitmap, mask)
            throw AssertionError("Failed to compare images", e)
        }

        if (!resultValidator(result)) {
            writeDiffImage(directories.failuresDir, fileName, bitmap, goldenBitmap, mask)
            throw AssertionError(
                "\"$resourceName\" failed to match reference image. ${result.pixelDifferences} pixels differ " +
                        "(${(result.pixelDifferences / result.pixelCount.toFloat()) * 100} %)"
            )
        }
    }

    private fun disableFlakyComponentsAndWaitForIdle(view: View? = null) {
        if (view != null) {
            disableAnimatedComponents(view)
        }
        if (notInAppMainThread()) {
            waitForAnimationsToFinish()
        }
    }

    private fun disableAnimatedComponents(view: View) {
        runOnUi {
            hideEditTextCursors(view)
            hideScrollViewBars(view)
        }
    }

    private fun hideEditTextCursors(view: View) {
        view.childrenViews<EditText>().forEach {
            it.isCursorVisible = false
        }
    }

    private fun hideScrollViewBars(view: View) {
        view.childrenViews<ScrollView>().forEach {
            hideViewBars(it)
        }

        view.childrenViews<HorizontalScrollView>().forEach {
            hideViewBars(it)
        }
    }

    private fun hideViewBars(it: View) {
        it.isHorizontalScrollBarEnabled = false
        it.isVerticalScrollBarEnabled = false
        it.overScrollMode = View.OVER_SCROLL_NEVER
    }

    public fun waitForAnimationsToFinish() {
        getInstrumentation().waitForIdleSync()
        Espresso.onIdle()
    }

    public fun runOnUi(block: () -> Unit) {
        if (notInAppMainThread()) {
            getInstrumentation().runOnMainSync { block() }
        } else {
            block()
        }
    }

    private fun notInAppMainThread() = Looper.myLooper() != Looper.getMainLooper()

}
