package com.runanywhere.kotlin_starter_example.debugger.services

data class VisionResult(
    val isCode: Boolean,
    val extractedCode: String?,
    val message: String
)