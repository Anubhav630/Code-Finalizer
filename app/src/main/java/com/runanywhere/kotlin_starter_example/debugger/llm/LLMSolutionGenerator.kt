package com.runanywhere.kotlin_starter_example.debugger.llm

import com.runanywhere.kotlin_starter_example.debugger.analysis.AnalysisReport
import com.runanywhere.kotlin_starter_example.debugger.input.CodeFile
import com.runanywhere.kotlin_starter_example.debugger.quality.QualityReport
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LLMSolutionGenerator {

    suspend fun generateDebugSolution(
        file: CodeFile,
        analysisReport: AnalysisReport
    ): LLMSolution = withContext(Dispatchers.IO) {

        val prompt = PromptBuilder.buildDebugPrompt(file, analysisReport.errors)

        val response = runCatching {
            RunAnywhere.chat(prompt)
        }.getOrElse {
            "LLM failure: ${it.message}"
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

        val response = runCatching {
            RunAnywhere.chat(prompt)
        }.getOrElse {
            "LLM failure: ${it.message}"
        }

        LLMSolution(
            originalQuery = prompt,
            generatedSolution = response,
            codeSnippet = extractCodeBlock(response),
            confidence = 0.75f
        )
    }

    private fun extractCodeBlock(response: String): String? {
        val start = response.indexOf("```")
        val end = response.lastIndexOf("```")
        if (start >= 0 && end > start) {
            return response.substring(start + 3, end)
                .lines()
                .drop(1)
                .joinToString("\n")
                .trim()
        }
        return null
    }

    private fun estimateConfidence(errorCount: Int): Float =
        when {
            errorCount == 0 -> 0.95f
            errorCount <= 3 -> 0.85f
            errorCount <= 8 -> 0.70f
            else -> 0.55f
        }
}