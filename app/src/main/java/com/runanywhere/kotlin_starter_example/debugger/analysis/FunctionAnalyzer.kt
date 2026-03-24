package com.runanywhere.kotlin_starter_example.debugger.analysis

import com.runanywhere.kotlin_starter_example.debugger.input.CodeFile

object FunctionAnalyzer {

    fun analyze(codeFile: CodeFile): List<String> {

        val code = codeFile.normalizedContent
        val insights = mutableListOf<String>()

        val functionCount = Regex("\\bfun\\b|\\bvoid\\b|\\bint\\b|\\bString\\b")
            .findAll(code).count()

        if (functionCount >= 8) {
            insights.add("Large number of functions detected — consider modularization.")
        }

        val lines = code.lines()

        if (lines.size >= 250) {
            insights.add("File is very large (${lines.size} lines) — high maintenance risk.")
        }

        val decisionPoints = Regex("\\bif\\b|\\bfor\\b|\\bwhile\\b|\\bcase\\b")
            .findAll(code).count()

        if (decisionPoints >= 15) {
            insights.add("High cyclomatic decision density — logic may be error-prone.")
        }

        return insights
    }
}