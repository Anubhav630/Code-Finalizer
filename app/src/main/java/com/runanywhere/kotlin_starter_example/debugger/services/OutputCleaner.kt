package com.runanywhere.kotlin_starter_example.utils

object OutputCleaner {

    fun clean(raw: String): String {
        return raw
            .replace("```", "")
            .replace("java", "")
            .replace("cpp", "")
            .replace("python", "")
            .trim()
    }

    fun isValid(output: String): Boolean {
        if (output.length < 10) return false
        if (output.contains("This looks like")) return false
        return true
    }
}