package com.runanywhere.kotlin_starter_example.debugger.llm

//import ai.runanywhere.sdk.RunAnywhere
import com.runanywhere.kotlin_starter_example.debugger.analysis.AnalysisReport
import com.runanywhere.kotlin_starter_example.debugger.analysis.DebugError
import com.runanywhere.kotlin_starter_example.debugger.input.CodeFile
import com.runanywhere.kotlin_starter_example.debugger.quality.QualityReport
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────
//  LLM Solution Models
// ─────────────────────────────────────────────

data class LLMSolution(
    val originalQuery: String,
    val generatedSolution: String,
    val codeSnippet: String?,      // Extracted code block if any
    val confidence: Float,
    val modelUsed: String = "SmolLM2-360M (on-device)"
)

data class FullDebugSession(
    val codeFile: CodeFile,
    val analysisReport: AnalysisReport,
    val qualityReport: QualityReport,
    val llmSolution: LLMSolution?,
    val sessionDurationMs: Long
)

// ─────────────────────────────────────────────
//  Prompt Builder
// ─────────────────────────────────────────────

object PromptBuilder {

    /**
     * Build a focused, concise prompt for on-device LLM (SmolLM2 360M).
     * Keep it short since the model has limited context window.
     */
    fun buildDebugPrompt(
        file: CodeFile,
        errors: List<DebugError>,
        maxCodeLines: Int = 40
    ): String {
        val codeSnippet = file.normalizedContent.lines()
            .take(maxCodeLines)
            .joinToString("\n")

        val topErrors = errors.take(5)  // Focus LLM on top 5 errors only
        val errorSummary = topErrors.joinToString("\n") { err ->
            "Line ${err.lineNumber}: [${err.errorType.name}] ${err.message}"
        }

        return """
You are a code debugger. Analyze this ${file.language.displayName} code and fix the listed errors.

CODE:
```${file.language.displayName.lowercase()}
$codeSnippet
```

ERRORS FOUND:
$errorSummary

Provide:
1. Root cause of each error
2. Fixed code snippet
3. One-line explanation per fix

Be concise.
""".trimIndent()
    }

    fun buildQualityPrompt(
        file: CodeFile,
        qualityReport: QualityReport,
        maxCodeLines: Int = 30
    ): String {
        val codeSnippet = file.normalizedContent.lines()
            .take(maxCodeLines)
            .joinToString("\n")

        val topIssues = qualityReport.issues.take(5)
        val issueSummary = topIssues.joinToString("\n") { issue ->
            "Line ${issue.lineNumber}: [${issue.category.name}/${issue.severity.label}] ${issue.improvementHint}"
        }

        return """
You are a code quality expert. Review this ${file.language.displayName} code (quality score: ${qualityReport.overallScore}/100).

CODE:
```${file.language.displayName.lowercase()}
$codeSnippet
```

TOP QUALITY ISSUES:
$issueSummary

Complexity: Cyclomatic=${qualityReport.complexityMetrics.cyclomaticComplexity}, MaxNesting=${qualityReport.complexityMetrics.maxNestingDepth}

Provide refactored code fixing the top issues. Be brief.
""".trimIndent()
    }

    fun buildGeneralAskPrompt(code: String, language: String, question: String): String = """
You are an expert ${language} developer. Answer this question about the code concisely.

CODE:
```${language.lowercase()}
${code.take(1500)}
```

QUESTION: $question

Answer:
""".trimIndent()
}

// ─────────────────────────────────────────────
//  LLM Solution Generator
// ─────────────────────────────────────────────

class LLMSolutionGenerator {

    /**
     * Generate a fix/solution using the on-device RunAnywhere SmolLM2 model.
     * This is the "Phase 2 LLM layer" — called AFTER static analysis.
     */
    suspend fun generateDebugSolution(
        file: CodeFile,
        analysisReport: AnalysisReport
    ): LLMSolution = withContext(Dispatchers.IO) {
        val prompt = PromptBuilder.buildDebugPrompt(file, analysisReport.errors)
        val response = runCatching { RunAnywhere.chat(prompt) }.getOrElse { e ->
            "Could not generate solution: ${e.message}. " +
                    "Ensure the LLM model is downloaded in the app."
        }
        LLMSolution(
            originalQuery = prompt,
            generatedSolution = response,
            codeSnippet = extractCodeBlock(response),
            confidence = estimateConfidence(analysisReport.errors.size)
        )
    }

    suspend fun generateQualitySolution(
        file: CodeFile,
        qualityReport: QualityReport
    ): LLMSolution = withContext(Dispatchers.IO) {
        val prompt = PromptBuilder.buildQualityPrompt(file, qualityReport)
        val response = runCatching { RunAnywhere.chat(prompt) }.getOrElse { e ->
            "Could not generate quality suggestions: ${e.message}"
        }
        LLMSolution(
            originalQuery = prompt,
            generatedSolution = response,
            codeSnippet = extractCodeBlock(response),
            confidence = 0.75f
        )
    }

    suspend fun askQuestion(
        file: CodeFile,
        question: String
    ): LLMSolution = withContext(Dispatchers.IO) {
        val prompt = PromptBuilder.buildGeneralAskPrompt(
            file.normalizedContent,
            file.language.displayName,
            question
        )
        val response = runCatching { RunAnywhere.chat(prompt) }.getOrElse { e ->
            "LLM error: ${e.message}"
        }
        LLMSolution(
            originalQuery = question,
            generatedSolution = response,
            codeSnippet = extractCodeBlock(response),
            confidence = 0.70f
        )
    }

    /** Stream LLM response token-by-token for real-time UI */
    fun streamDebugSolution(file: CodeFile, analysisReport: AnalysisReport): Flow<String> = flow {
        val prompt = PromptBuilder.buildDebugPrompt(file, analysisReport.errors)
        // RunAnywhere SDK doesn't expose streaming in the starter; simulate with chunked response
        val fullResponse = withContext(Dispatchers.IO) {
            runCatching { RunAnywhere.chat(prompt) }.getOrElse { "Error: ${it.message}" }
        }
        // Emit word by word to simulate streaming effect
        fullResponse.split(" ").forEach { word ->
            emit("$word ")
            kotlinx.coroutines.delay(30)
        }
    }

    private fun extractCodeBlock(response: String): String? {
        val start = response.indexOf("```")
        val end = response.lastIndexOf("```")
        if (start >= 0 && end > start) {
            val block = response.substring(start + 3, end)
            // Remove language identifier on first line
            return block.lines().drop(1).joinToString("\n").trim()
        }
        return null
    }

    private fun estimateConfidence(errorCount: Int): Float = when {
        errorCount == 0 -> 0.95f
        errorCount <= 3 -> 0.85f
        errorCount <= 8 -> 0.70f
        else -> 0.55f
    }
}

// ─────────────────────────────────────────────
//  Full Pipeline Orchestrator
// ─────────────────────────────────────────────

class CodeFinalizerPipeline {

    private val debugEngine = com.runanywhere.kotlin_starter_example.debugger.analysis.CoreDebugEngine()
    private val qualityEvaluator = com.runanywhere.kotlin_starter_example.debugger.quality.QualityEvaluator()
    private val llmGenerator = LLMSolutionGenerator()

    /**
     * Run the complete 3-phase pipeline on a single CodeFile:
     *   Phase 1 (input) → Phase 2 (syntax/semantic) → Phase 3 (quality) → LLM solution
     */
    suspend fun runFullPipeline(
        file: CodeFile,
        generateLLMSolution: Boolean = true
    ): FullDebugSession = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()

        // Phase 2 — Syntax + Semantic
        val analysisReport = debugEngine.analyzeFile(file)

        // Phase 3 — Quality
        val qualityReport = qualityEvaluator.evaluateFile(file)

        // LLM — Only if there are actual errors or quality is low
        val llmSolution = if (generateLLMSolution &&
            (analysisReport.errors.isNotEmpty() || qualityReport.overallScore < 70)) {
            if (analysisReport.errors.isNotEmpty()) {
                llmGenerator.generateDebugSolution(file, analysisReport)
            } else {
                llmGenerator.generateQualitySolution(file, qualityReport)
            }
        } else null

        FullDebugSession(
            codeFile = file,
            analysisReport = analysisReport,
            qualityReport = qualityReport,
            llmSolution = llmSolution,
            sessionDurationMs = System.currentTimeMillis() - start
        )
    }

    /** Run on multiple files (e.g., from ZIP) */
    suspend fun runOnProject(files: List<CodeFile>): List<FullDebugSession> =
        files.map { runFullPipeline(it, generateLLMSolution = false) }
            .also { sessions ->
                // Generate one LLM summary for the whole project
                val worstFile = sessions.minByOrNull { it.qualityReport.overallScore }
                // Caller can request LLM on specific file after getting this list
            }
}
