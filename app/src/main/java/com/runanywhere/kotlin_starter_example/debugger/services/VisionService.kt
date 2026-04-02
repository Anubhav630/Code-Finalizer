//package com.runanywhere.kotlin_starter_example.debugger.services
//import android.content.Context
//import android.graphics.Bitmap
//import com.runanywhere.kotlin_starter_example.services.ModelService
//import com.runanywhere.sdk.public.RunAnywhere
//import com.runanywhere.sdk.public.extensions.VLM.VLMGenerationOptions
//import com.runanywhere.sdk.public.extensions.VLM.VLMImage
//import com.runanywhere.sdk.public.extensions.processImageStream
//import java.io.File
//import java.io.FileOutputStream
//import com.runanywhere.kotlin_starter_example.utils.ImagePreprocessor
//import com.runanywhere.kotlin_starter_example.utils.OutputCleaner
//
//
//class VisionService(private val context: Context,private val modelService: ModelService
//) {
//
//    suspend fun extractCodeFromImage(bitmap: Bitmap): VisionResult {
//
//        return try {
//
//            if (!modelService.isVLMLoaded) {
//                modelService.downloadAndLoadVLM()
//
//                // wait until model loads
//                while (!modelService.isVLMLoaded) {
//                    kotlinx.coroutines.delay(300)
//                }
//            }
//
//
//            // ⭐ Save bitmap to temp file (SDK requires file path)
//            val processedBitmap = ImagePreprocessor.preprocess(bitmap)
//            val tempFile =
//                File(context.cacheDir, "debug_code_${System.currentTimeMillis()}.png")
//
//            FileOutputStream(tempFile).use { out ->
//                processedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
//                if (!tempFile.exists() || tempFile.length() == 0L) {
//                    return VisionResult(false, null, "Image file not saved properly")
//                }
//            }
//
//            //modelService.downloadAndLoadVLM()
//
//            val vlmImage = VLMImage.fromFilePath(tempFile.absolutePath)
//
//
//            val isCodeDetected = detectIfCode(vlmImage)
//            if (!isCodeDetected) {
//                return VisionResult(
//                    isCode = false,
//                    extractedCode = null,
//                    message = "Image does not contain programming code"
//                )
//            }
//
//
//
//            val prompt = """
//You are an OCR system specialized in extracting programming code.
//
//Rules:
//1. Extract ALL visible code exactly
//2. Preserve indentation and formatting
//3. Do NOT explain anything
//4. Do NOT add extra text
//
//Output ONLY code.
//
//If no code present, output exactly:
//NOT_CODE
//""".trimIndent()
//
//
//
//            val options = VLMGenerationOptions(maxTokens = 1200)
//
//            var response = ""
//
//            RunAnywhere.processImageStream(vlmImage, prompt, options)
//                .collect { token ->
//                    response += token
//                }
//
//            if (response.trim().equals("NOT_CODE", ignoreCase = true)) {
//
//                VisionResult(
//                    isCode = false,
//                    extractedCode = null,
//                    message = "Image does not contain programming code"
//                )
//
//            } else {
//
//                val cleaned = OutputCleaner.clean(response)
//
//                val finalOutput =
//                    if (!OutputCleaner.isValid(cleaned)) {
//
//                        var retry = ""
//
//                        RunAnywhere.processImageStream(
//                            vlmImage,
//                            "STRICTLY OUTPUT ONLY CODE. NO TEXT.",
//                            options
//                        ).collect { token ->
//                            retry += token
//                        }
//
//                        OutputCleaner.clean(retry)
//                    } else {
//                        cleaned
//                    }
//
//                VisionResult(
//                    isCode = true,
//                    extractedCode = finalOutput,
//                    message = "Code extracted successfully"
//                )
//            }
//
//        } catch (e: Exception) {
//
//            VisionResult(
//                isCode = false,
//                extractedCode = null,
//                message = "VLM error: ${e.message}"
//            )
//        }
//
//
//    }
//    private suspend fun detectIfCode(vlmImage: VLMImage): Boolean {
//
//        val prompt = "Does this image contain programming code? Answer ONLY YES or NO."
//
//        var response = ""
//
//        RunAnywhere.processImageStream(vlmImage, prompt, VLMGenerationOptions(maxTokens = 20))
//            .collect { token ->
//                response += token
//            }
//
//        return response.trim().equals("YES", ignoreCase = true)
//    }
//}


package com.runanywhere.kotlin_starter_example.debugger.services

import android.content.Context
import android.graphics.Bitmap
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.utils.ImagePreprocessor
import com.runanywhere.kotlin_starter_example.utils.OutputCleaner
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.VLM.VLMGenerationOptions
import com.runanywhere.sdk.public.extensions.VLM.VLMImage
import com.runanywhere.sdk.public.extensions.processImageStream
import java.io.File
import java.io.FileOutputStream

class VisionService(
    private val context: Context,
    private val modelService: ModelService
) {

    suspend fun extractCodeFromImage(bitmap: Bitmap): VisionResult {

        return try {

            // ✅ Ensure model loaded (with safety)
            if (!modelService.isVLMLoaded) {
                modelService.downloadAndLoadVLM()

                var retries = 0
                while (!modelService.isVLMLoaded && retries < 20) {
                    kotlinx.coroutines.delay(300)
                    retries++
                }

                if (!modelService.isVLMLoaded) {
                    return VisionResult(false, null, "Model failed to load")
                }
            }

            // ✅ FORCE SAFE BITMAP (fix hardware bitmap crash)
            val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

            // ✅ Preprocess
            val processedBitmap = ImagePreprocessor.preprocess(safeBitmap)

            // ✅ Save image
            val tempFile = File(
                context.cacheDir,
                "debug_code_${System.currentTimeMillis()}.png"
            )

            FileOutputStream(tempFile).use { out ->
                processedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // ✅ Validate file AFTER saving
            if (!tempFile.exists() || tempFile.length() == 0L) {
                return VisionResult(false, null, "Image file not saved properly")
            }

            val vlmImage = VLMImage.fromFilePath(tempFile.absolutePath)

            // ❌ REMOVED detection step (IMPORTANT FIX)

            val prompt = """
You are an OCR system specialized in extracting programming code.

Rules:
1. Extract ALL visible code exactly
2. Preserve indentation and formatting
3. Do NOT explain anything
4. Do NOT add extra text

Output ONLY code.

If no code present, output exactly:
NOT_CODE
""".trimIndent()

            val options = VLMGenerationOptions(maxTokens = 800)

            var response = ""

            RunAnywhere.processImageStream(vlmImage, prompt, options)
                .collect { token ->
                    response += token
                }

            // ✅ Handle empty response
            if (response.isBlank()) {
                return VisionResult(false, null, "Model returned empty response")
            }

            // ✅ Handle NOT_CODE properly
            if (response.trim().equals("NOT_CODE", ignoreCase = true)) {
                return VisionResult(false, null, "Image does not contain programming code")
            }

            // ✅ Clean output
            val cleaned = OutputCleaner.clean(response)

            val finalOutput = cleaned
//                if (!OutputCleaner.isValid(cleaned)) {
//
//                    var retry = ""
//
//                    RunAnywhere.processImageStream(
//                        vlmImage,
//                        "STRICTLY OUTPUT ONLY CODE. NO TEXT.",
//                        options
//                    ).collect { token ->
//                        retry += token
//                    }
//
//                    OutputCleaner.clean(retry)
//                } else {
//                    cleaned
//                }

            VisionResult(
                isCode = true,
                extractedCode = finalOutput,
                message = "Code extracted successfully"
            )

        } catch (e: Exception) {

            VisionResult(
                isCode = false,
                extractedCode = null,
                message = "VLM error: ${e.message}"
            )
        }
    }
}