package com.runanywhere.kotlin_starter_example.debugger.analysis

import com.runanywhere.kotlin_starter_example.debugger.input.CodeFile

data class ProjectInsights(
    val worstFile: String?,
    val healthScore: Int,
    val summary: String
)

object ProjectAnalyzer {

    fun analyze(files: List<CodeFile>): ProjectInsights {

        if (files.isEmpty()) {
            return ProjectInsights(null, 100, "No files to analyze.")
        }

        var worstFile: CodeFile? = null
        var worstScore = -1

        var totalComplexity = 0

        files.forEach { file ->

            val flow = FlowAnalyzer.analyze(file).size
            val func = FunctionAnalyzer.analyze(file).size

            val riskScore = flow * 2 + func * 3

            totalComplexity += riskScore

            if (riskScore > worstScore) {
                worstScore = riskScore
                worstFile = file
            }
        }

        val avgRisk = totalComplexity / files.size
        val healthScore = (100 - avgRisk * 5).coerceIn(10, 100)

        val summary = when {
            healthScore >= 80 -> "Project structure looks stable with manageable complexity."
            healthScore >= 60 -> "Moderate structural risk detected. Some files may need refactoring."
            else -> "High architectural complexity detected. Consider redesign or modularization."
        }

        return ProjectInsights(
            worstFile = worstFile?.fileName,
            healthScore = healthScore,
            summary = summary
        )
    }
}