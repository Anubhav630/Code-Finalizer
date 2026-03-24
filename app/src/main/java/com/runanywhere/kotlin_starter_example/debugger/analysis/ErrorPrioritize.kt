package com.runanywhere.kotlin_starter_example.debugger.analysis

object ErrorPrioritizer {

    fun prioritize(errors: List<DebugError>): List<DebugError> {

        if (errors.isEmpty()) return errors

        // Step 1 — sort by severity ordinal (higher severity first)
        val sorted = errors.sortedByDescending { it.severity.ordinal }

        // Step 2 — detect primary syntax / bracket issue
        val primarySyntax = sorted.firstOrNull {
            it.errorType.name.contains("BRACKET", ignoreCase = true) ||
                    it.errorType.name.contains("SYNTAX", ignoreCase = true)
        }

        return if (primarySyntax != null) {
            listOf(primarySyntax) +
                    sorted.filter { it != primarySyntax }.take(4)
        } else {
            sorted.take(5)
        }
    }
}