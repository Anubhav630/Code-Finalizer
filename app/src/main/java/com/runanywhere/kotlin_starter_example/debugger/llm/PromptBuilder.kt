package com.runanywhere.kotlin_starter_example.debugger.llm

import com.runanywhere.kotlin_starter_example.debugger.analysis.DebugError
import com.runanywhere.kotlin_starter_example.debugger.analysis.ErrorPrioritizer
import com.runanywhere.kotlin_starter_example.debugger.analysis.FlowAnalyzer
import com.runanywhere.kotlin_starter_example.debugger.analysis.FunctionAnalyzer
import com.runanywhere.kotlin_starter_example.debugger.input.CodeFile
import com.runanywhere.kotlin_starter_example.debugger.quality.QualityReport

object PromptBuilder {

    fun buildDebugPrompt(
        file: CodeFile,
        errors: List<DebugError>
    ): String {

        val snippet = file.normalizedContent
            .lines()
            .take(40)
            .joinToString("\n")

        val prioritized = ErrorPrioritizer.prioritize(errors)

        val errorSummary = prioritized.joinToString("\n") {
            "Line ${it.lineNumber}: ${it.message}"
        }

        val flowInsights = FlowAnalyzer.analyze(file)
        val functionInsights = FunctionAnalyzer.analyze(file)

        return """
You are an expert ${file.language.displayName} debugger.

CODE:
$snippet

ERRORS:
$errorSummary

FLOW INSIGHTS:
${flowInsights.joinToString("\n")}

FUNCTION COMPLEXITY:
${functionInsights.joinToString("\n")}

Explain the root cause and provide corrected code.
""".trimIndent()
    }

    fun buildQualityPrompt(
        file: CodeFile,
        quality: QualityReport
    ): String {

        val snippet = file.normalizedContent
            .lines()
            .take(30)
            .joinToString("\n")

        val issues = quality.issues.take(5).joinToString("\n") {
            "Line ${it.lineNumber}: ${it.improvementHint}"
        }

        val flowInsights = FlowAnalyzer.analyze(file)
        val functionInsights = FunctionAnalyzer.analyze(file)

        return """
You are a senior code reviewer.

Quality Score: ${quality.overallScore}/100

CODE:
$snippet

QUALITY ISSUES:
$issues

FLOW INSIGHTS:
${flowInsights.joinToString("\n")}

FUNCTION COMPLEXITY:
${functionInsights.joinToString("\n")}

Suggest refactored and optimized code.
""".trimIndent()
    }


}