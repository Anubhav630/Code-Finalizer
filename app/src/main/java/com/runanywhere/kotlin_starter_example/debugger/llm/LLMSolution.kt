package com.runanywhere.kotlin_starter_example.debugger.llm

data class LLMSolution(
    val originalQuery: String,
    val generatedSolution: String,
    val codeSnippet: String?,
    val confidence: Float,
    val modelUsed: String = "RunAnywhere On-Device"
)