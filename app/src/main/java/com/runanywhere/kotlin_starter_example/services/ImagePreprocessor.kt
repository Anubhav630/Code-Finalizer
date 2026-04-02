package com.runanywhere.kotlin_starter_example.services

import android.graphics.Bitmap

annotation class ImagePreprocessor {
    companion object {
        fun preprocess(bitmap: android.graphics.Bitmap) {}
    }
}
