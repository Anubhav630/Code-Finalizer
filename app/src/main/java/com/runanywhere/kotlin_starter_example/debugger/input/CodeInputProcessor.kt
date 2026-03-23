package com.runanywhere.kotlin_starter_example.debugger.input

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ─────────────────────────────────────────────
//  Data Models
// ─────────────────────────────────────────────

data class CodeFile(
    val fileName: String,
    val language: SupportedLanguage,
    val rawContent: String,
    val normalizedContent: String,
    val lineCount: Int
)

enum class SupportedLanguage(val extensions: List<String>, val displayName: String) {
    PYTHON(listOf("py"), "Python"),
    JAVA(listOf("java"), "Java"),
    KOTLIN(listOf("kt", "kts"), "Kotlin"),
    JAVASCRIPT(listOf("js", "mjs"), "JavaScript"),
    TYPESCRIPT(listOf("ts", "tsx"), "TypeScript"),
    CPP(listOf("cpp", "cc", "cxx", "c++"), "C++"),
    C(listOf("c"), "C"),
    GO(listOf("go"), "Go"),
    RUST(listOf("rs"), "Rust"),
    UNKNOWN(listOf(), "Unknown");

    companion object {
        fun fromExtension(ext: String): SupportedLanguage =
            values().firstOrNull { ext.lowercase() in it.extensions } ?: UNKNOWN
    }
}

sealed class InputResult {
    data class Success(val files: List<CodeFile>) : InputResult()
    data class Error(val message: String) : InputResult()
}

// ─────────────────────────────────────────────
//  Phase 1A — ZIP Input Handler
// ─────────────────────────────────────────────

object ZipInputHandler {

    private val SUPPORTED_EXTENSIONS = SupportedLanguage.values()
        .flatMap { it.extensions }
        .toSet()

    private val SKIP_DIRS = setOf(
        "node_modules", ".git", ".gradle", "build",
        "__pycache__", ".idea", "dist", "out", "target"
    )

    suspend fun extractFromZip(inputStream: InputStream): List<CodeFile> =
        withContext(Dispatchers.IO) {
            val files = mutableListOf<CodeFile>()
            ZipInputStream(inputStream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && !isSkippedPath(entry.name)) {
                        val ext = entry.name.substringAfterLast('.', "")
                        if (ext in SUPPORTED_EXTENSIONS) {
                            val content = zip.bufferedReader().readText()
                            val language = SupportedLanguage.fromExtension(ext)
                            val normalized = CodeNormalizer.normalize(content, language)
                            files.add(
                                CodeFile(
                                    fileName = entry.name,
                                    language = language,
                                    rawContent = content,
                                    normalizedContent = normalized,
                                    lineCount = normalized.lines().size
                                )
                            )
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            files
        }

    private fun isSkippedPath(path: String): Boolean =
        SKIP_DIRS.any { skip -> path.split("/").any { it.equals(skip, ignoreCase = true) } }
}

// ─────────────────────────────────────────────
//  Phase 1B — Image OCR Handler (ML Kit)
// ─────────────────────────────────────────────

object ImageOcrHandler {

    suspend fun extractFromBitmap(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val raw = result.text
                    cont.resume(cleanOcrText(raw))
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

    /**
     * Post-process OCR output:
     * - Fix common OCR character confusions (0↔O, 1↔l, etc.)
     * - Remove noise artifacts
     * - Preserve indentation structure
     */
    private fun cleanOcrText(raw: String): String {
        return raw.lines()
            .map { line ->
                line
                    // Common OCR character fixes in code context
                    .replace(Regex("(?<=[0-9])O(?=[0-9])"), "0")  // O between digits → 0
                    .replace(Regex("(?<=[a-zA-Z])0(?=[a-zA-Z])"), "O")  // 0 between letters → O
                    // Remove stray non-printable chars but preserve tabs/newlines
                    .replace(Regex("[^\u0009\u0020-\u007E]"), "")
                    // Fix common bracket confusions
                    .replace("｛", "{").replace("｝", "}")
                    .replace("（", "(").replace("）", ")")
            }
            .joinToString("\n")
    }

    fun inferLanguageFromOcrText(text: String): SupportedLanguage {
        val hints = mapOf(
            SupportedLanguage.PYTHON to listOf("def ", "import ", "print(", "elif ", "self."),
            SupportedLanguage.JAVA to listOf("public class", "System.out", "void ", "import java"),
            SupportedLanguage.KOTLIN to listOf("fun ", "val ", "var ", "data class", "println("),
            SupportedLanguage.JAVASCRIPT to listOf("function ", "const ", "let ", "var ", "=>"),
            SupportedLanguage.TYPESCRIPT to listOf(": string", ": number", "interface ", "type "),
            SupportedLanguage.CPP to listOf("#include", "std::", "cout <<", "int main("),
            SupportedLanguage.GO to listOf("package main", "func ", "fmt.Print", ":= "),
            SupportedLanguage.RUST to listOf("fn main()", "let mut", "println!", "impl ")
        )
        var bestMatch = SupportedLanguage.UNKNOWN
        var bestScore = 0
        hints.forEach { (lang, keywords) ->
            val score = keywords.count { text.contains(it) }
            if (score > bestScore) { bestScore = score; bestMatch = lang }
        }
        return bestMatch
    }
}

// ─────────────────────────────────────────────
//  Phase 1C — Code Normalizer Module
// ─────────────────────────────────────────────

object CodeNormalizer {

    fun normalize(raw: String, language: SupportedLanguage): String {
        var code = raw
        code = removeNoise(code)
        code = fixIndentation(code, language)
        code = removeBlankLineExcess(code)
        return code.trimEnd()
    }

    /** Remove trailing whitespace, BOM, null bytes */
    private fun removeNoise(code: String): String =
        code
            .replace("\u0000", "")       // null bytes
            .replace("\uFEFF", "")       // BOM
            .replace("\r\n", "\n")       // normalize line endings
            .replace("\r", "\n")
            .lines()
            .map { it.trimEnd() }        // trailing whitespace per line
            .joinToString("\n")

    /** Detect and normalize mixed tabs/spaces to consistent 4-space indentation */
    private fun fixIndentation(code: String, language: SupportedLanguage): String {
        val lines = code.lines()
        val usesTabs = lines.count { it.startsWith("\t") }
        val usesSpaces = lines.count { it.startsWith("  ") }

        return if (usesTabs > usesSpaces) {
            // Convert tabs to 4 spaces
            lines.joinToString("\n") { line ->
                buildString {
                    var i = 0
                    while (i < line.length && line[i] == '\t') {
                        append("    ")
                        i++
                    }
                    append(line.substring(i))
                }
            }
        } else {
            code
        }
    }

    /** Collapse 3+ consecutive blank lines into 2 */
    private fun removeBlankLineExcess(code: String): String {
        val lines = code.lines()
        val result = mutableListOf<String>()
        var blankCount = 0
        for (line in lines) {
            if (line.isBlank()) {
                blankCount++
                if (blankCount <= 2) result.add(line)
            } else {
                blankCount = 0
                result.add(line)
            }
        }
        return result.joinToString("\n")
    }
}

// ─────────────────────────────────────────────
//  Phase 1 — Main Entry Point
// ─────────────────────────────────────────────

class CodeInputProcessor(private val context: Context) {

    /** Process a ZIP file URI → list of CodeFiles */
    suspend fun processZip(uri: Uri): InputResult = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext InputResult.Error("Cannot open file")
            val files = ZipInputHandler.extractFromZip(stream)
            if (files.isEmpty())
                InputResult.Error("No supported code files found in ZIP")
            else
                InputResult.Success(files)
        } catch (e: Exception) {
            InputResult.Error("ZIP processing failed: ${e.message}")
        }
    }

    /** Process a code image → single CodeFile */
    suspend fun processImage(bitmap: Bitmap): InputResult {
        return try {
            val ocrText = ImageOcrHandler.extractFromBitmap(bitmap)
            if (ocrText.isBlank()) return InputResult.Error("No text detected in image")
            val language = ImageOcrHandler.inferLanguageFromOcrText(ocrText)
            val normalized = CodeNormalizer.normalize(ocrText, language)
            InputResult.Success(
                listOf(
                    CodeFile(
                        fileName = "image_input.${language.extensions.firstOrNull() ?: "txt"}",
                        language = language,
                        rawContent = ocrText,
                        normalizedContent = normalized,
                        lineCount = normalized.lines().size
                    )
                )
            )
        } catch (e: Exception) {
            InputResult.Error("Image OCR failed: ${e.message}")
        }
    }

    /** Process raw pasted code text */
    suspend fun processText(code: String, hintLanguage: SupportedLanguage? = null): InputResult =
        withContext(Dispatchers.IO) {
            val language = hintLanguage ?: ImageOcrHandler.inferLanguageFromOcrText(code)
            val normalized = CodeNormalizer.normalize(code, language)
            InputResult.Success(
                listOf(
                    CodeFile(
                        fileName = "pasted_code.${language.extensions.firstOrNull() ?: "txt"}",
                        language = language,
                        rawContent = code,
                        normalizedContent = normalized,
                        lineCount = normalized.lines().size
                    )
                )
            )
        }
}
