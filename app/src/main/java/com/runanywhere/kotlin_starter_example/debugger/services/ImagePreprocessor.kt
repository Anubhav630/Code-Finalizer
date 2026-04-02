//package com.runanywhere.kotlin_starter_example.utils
//
//import android.graphics.*
//
//object ImagePreprocessor {
//
//    fun preprocess(input: Bitmap): Bitmap {
//
//        val safeBitmap = if (input.config == Bitmap.Config.HARDWARE) {
//            input.copy(Bitmap.Config.ARGB_8888, true)
//        } else {
//            input.copy(Bitmap.Config.ARGB_8888, true)
//        }
//
//        val gray = toGrayscale(input)
//        return increaseContrast(gray)
//    }
//
//    private fun toGrayscale(src: Bitmap): Bitmap {
//        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(bmp)
//        val paint = Paint()
//
//        val cm = ColorMatrix()
//        cm.setSaturation(0f)
//
//        paint.colorFilter = ColorMatrixColorFilter(cm)
//        canvas.drawBitmap(src, 0f, 0f, paint)
//
//        return bmp
//    }
//
//    private fun increaseContrast(src: Bitmap): Bitmap {
//        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(bmp)
//        val paint = Paint()
//
//        val cm = ColorMatrix(
//            floatArrayOf(
//                1.5f, 0f, 0f, 0f, -30f,
//                0f, 1.5f, 0f, 0f, -30f,
//                0f, 0f, 1.5f, 0f, -30f,
//                0f, 0f, 0f, 1f, 0f
//            )
//        )
//
//        paint.colorFilter = ColorMatrixColorFilter(cm)
//        canvas.drawBitmap(src, 0f, 0f, paint)
//
//        return bmp
//    }
//}

package com.runanywhere.kotlin_starter_example.utils

import android.graphics.*

object ImagePreprocessor {

    fun preprocess(input: Bitmap): Bitmap {

        // 🔥 FORCE SOFTWARE BITMAP (THIS IS THE FIX)
        val safeBitmap = input.copy(Bitmap.Config.ARGB_8888, true)
        val resized = Bitmap.createScaledBitmap(
            safeBitmap,
            768,   // 🔥 optimal size
            (safeBitmap.height * 768) / safeBitmap.width,
            true
        )
        val gray = toGrayscale(safeBitmap)
        return increaseContrast(gray)
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(
            src.width,
            src.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bmp)
        val paint = Paint()

        val cm = ColorMatrix()
        cm.setSaturation(0f)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)

        return bmp
    }

    private fun increaseContrast(src: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(
            src.width,
            src.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bmp)
        val paint = Paint()

        val cm = ColorMatrix(
            floatArrayOf(
                1.5f, 0f, 0f, 0f, -30f,
                0f, 1.5f, 0f, 0f, -30f,
                0f, 0f, 1.5f, 0f, -30f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)

        return bmp
    }
}