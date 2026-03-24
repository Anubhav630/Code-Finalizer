package com.runanywhere.kotlin_starter_example.debugger.analysis

import com.runanywhere.kotlin_starter_example.debugger.input.CodeFile

object FlowAnalyzer {

    fun analyze(codeFile: CodeFile): List<String> {

        val code = codeFile.normalizedContent
        val warnings = mutableListOf<String>()

        val loopCount = Regex("\\b(for|while)\\b").findAll(code).count()
        val ifCount = Regex("\\bif\\b").findAll(code).count()

        if (loopCount >= 3)
            warnings.add("High number of loops may increase runtime complexity.")

        if (ifCount >= 5)
            warnings.add("Deep branching detected — logic may be hard to maintain.")

        if (code.contains("while(true)") || code.contains("for(;;)"))
            warnings.add("Potential infinite loop detected.")

        val nestingDepth = estimateMaxNestingDepth(code)
        if (nestingDepth >= 4)
            warnings.add("Deep nesting level ($nestingDepth) — consider refactoring.")

        return warnings
    }

    private fun estimateMaxNestingDepth(code: String): Int {
        var depth = 0
        var maxDepth = 0
        code.forEach {
            if (it == '{') {
                depth++
                if (depth > maxDepth) maxDepth = depth
            }
            if (it == '}') depth--
        }
        return maxDepth
    }
}