package com.telefonica.loggerazzi

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.createBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.screenshot.Screenshot
import com.dropbox.differ.ImageComparator
import com.dropbox.differ.Mask
import com.dropbox.differ.SimpleImageComparator
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

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val downloadDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    )
    private val loggerazziDir = File(downloadDir, "loggerazzi-logs/${context.packageName}")
    private val failuresDir = File(loggerazziDir, "failures")
    private val recordedDir = File(loggerazziDir, "recorded")

    override fun apply(base: Statement, description: Description): Statement {
        className = description.className
        testName = description.methodName
        isTestIgnored = description.getAnnotation(IgnoreScreenshots::class.java) != null

        if (!failuresDir.exists()) {
            failuresDir.mkdirs()
        }
        if (!recordedDir.exists()) {
            recordedDir.mkdirs()
        }
        return base
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public fun compareScreenshot(
        rule: ComposeTestRule,
        name: String?,
    ) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        compareScreenshot(bitmap, name)
    }

    public fun compareScreenshot(
        activity: Activity,
        name: String?,
    ) {
        val bitmap = Screenshot.capture(activity).bitmap
        compareScreenshot(bitmap, name)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    public fun compareScreenshot(
        bitmap: Bitmap,
        name: String?
    ) {
        val resourceName = "${className}_${name ?: testName}.png"
        val fileName = "$resourceName.${System.nanoTime()}"
        saveScreenshot(fileName, bitmap)

        if (InstrumentationRegistry.getArguments().getString("record") != "true" && !isTestIgnored) {
            val goldenBitmap = try {
                context.assets.open("loggerazzi-golden-files/$resourceName").use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (e: FileNotFoundException) {
                throw IllegalStateException(
                    "Failed to find golden image named $resourceName. If this is a new test, you may need to record screenshots",
                    e
                )
            }

            if (bitmap.width != goldenBitmap.width || bitmap.height != goldenBitmap.height) {
                writeDiffImage(fileName, bitmap, goldenBitmap, null)
                throw AssertionError(
                    "$name: Test image (w=${bitmap.width}, h=${bitmap.height}) differs in size" +
                            " from reference image (w=${goldenBitmap.width}, h=${goldenBitmap.height}).\n",
                )
            }

            val mask = Mask(bitmap.width, bitmap.height)
            val result = try {
                imageComparator.compare(BitmapImage(goldenBitmap), BitmapImage(bitmap), mask)
            } catch (e: IllegalArgumentException) {
                writeDiffImage(fileName, bitmap, goldenBitmap, mask)
                throw AssertionError("Failed to compare images", e)
            }

            if (!resultValidator(result)) {
                writeDiffImage(fileName, bitmap, goldenBitmap, mask)
                throw AssertionError(
                    "\"$resourceName\" failed to match reference image. ${result.pixelDifferences} pixels differ " +
                            "(${(result.pixelDifferences / result.pixelCount.toFloat()) * 100} %)"
                )
            }
        }
    }

    private fun saveScreenshot(fileName: String, bitmap: Bitmap) {
        val testFile = File(recordedDir, fileName)
        testFile.createNewFile()
        testFile.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /**
     * Writes the given screenshot to the external reference image directory, returning the
     * file path of the file that was written.
     */
    private fun writeDiffImage(
        fileName: String,
        screenshot: Bitmap,
        referenceImage: Bitmap,
        mask: Mask?,
    ) {
        val diffFile = File(failuresDir, fileName)
        val diffImage = generateDiffImage(referenceImage, screenshot, mask)
        diffFile.outputStream().use {
            diffImage.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /**
     * Generates a `Bitmap` consisting of the reference image, the test image, and
     * an image that highlights the differences between the two.
     */
    private fun generateDiffImage(
        referenceImage: Bitmap,
        testImage: Bitmap,
        differenceMask: Mask?
    ): Bitmap {
        // Render the failed screenshots to an output image
        val maskWidth = differenceMask?.width ?: 0
        val maskHeight = differenceMask?.height ?: 0
        val output =
            createBitmap(
                width = referenceImage.width + testImage.width + maskWidth,
                height = maxOf(referenceImage.height, testImage.height, maskHeight)
            )
        val canvas = Canvas(output)
        canvas.drawBitmap(referenceImage, 0f, 0f, null)
        canvas.drawBitmap(testImage, referenceImage.width.toFloat() + maskWidth, 0f, null)

        // If we have a mask, draw it between the reference image and the test image.
        if (differenceMask != null) {
            canvas.drawBitmap(referenceImage, referenceImage.width.toFloat(), 0f, null)

            val diffPaint = Paint().apply {
                color = 0x3DFF0000
                strokeWidth = 0f
            }
            val otherPaint = Paint().apply {
                color = 0x3D000000
                strokeWidth = 0f
            }
            (0 until differenceMask.height).forEach { y ->
                (0 until differenceMask.width).forEach { x ->
                    val paint = if (differenceMask.getValue(x, y) > 0) diffPaint else otherPaint
                    canvas.drawPoint(referenceImage.width + x.toFloat(), y.toFloat(), paint)
                }
            }
        }
        return output
    }
}
