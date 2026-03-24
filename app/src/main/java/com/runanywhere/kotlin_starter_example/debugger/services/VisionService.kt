package com.runanywhere.kotlin_starter_example.debugger.services

import android.content.Context
import android.graphics.Bitmap
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.VLM.VLMGenerationOptions
import com.runanywhere.sdk.public.extensions.VLM.VLMImage
import com.runanywhere.sdk.public.extensions.processImageStream
import kotlinx.coroutines.flow.collect
import java.io.File
import java.io.FileOutputStream

class VisionService(private val context: Context,private val modelService: ModelService
) {

    suspend fun extractCodeFromImage(bitmap: Bitmap): VisionResult {

        return try {

            if (!modelService.isVLMLoaded) {
                modelService.downloadAndLoadVLM()

                // wait until model loads
                while (!modelService.isVLMLoaded) {
                    kotlinx.coroutines.delay(300)
                }
            }

            // ⭐ Save bitmap to temp file (SDK requires file path)
            val tempFile =
                File(context.cacheDir, "debug_code_${System.currentTimeMillis()}.jpg")

            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            modelService.downloadAndLoadVLM()

            val vlmImage = VLMImage.fromFilePath(tempFile.absolutePath)

            val prompt =
                "If this image contains programming code, extract the code exactly. " +
                        "If not code, respond with ONLY: NOT_CODE"

            val options = VLMGenerationOptions(maxTokens = 400)

            var response = ""

            RunAnywhere.processImageStream(vlmImage, prompt, options)
                .collect { token ->
                    response += token
                }

            if (response.contains("NOT_CODE", true)) {

                VisionResult(
                    isCode = false,
                    extractedCode = null,
                    message = "Image does not contain programming code"
                )

            } else {

                VisionResult(
                    isCode = true,
                    extractedCode = response,
                    message = "Code extracted successfully"
                )
            }

        } catch (e: Exception) {

            VisionResult(
                isCode = false,
                extractedCode = null,
                message = "VLM error: ${e.message}"
            )
        }
    }
}