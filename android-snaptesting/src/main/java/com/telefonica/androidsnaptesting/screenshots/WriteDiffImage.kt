/*Copyright (c) 2022 Dropbox, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

package com.telefonica.androidsnaptesting.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.dropbox.differ.Mask
import java.io.File

internal class WriteDiffImage {

    /**
     * Writes the given screenshot to the external reference image directory, returning the
     * file path of the file that was written.
     */
    operator fun invoke(
        failuresDir: File,
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
            for (y in 0 until differenceMask.height) {
                for (x in 0 until differenceMask.width) {
                    val paint = if (differenceMask.getValue(x, y) > 0) diffPaint else otherPaint
                    canvas.drawPoint(referenceImage.width + x.toFloat(), y.toFloat(), paint)
                }
            }
        }
        return output
    }
}
