package com.runanywhere.kotlin_starter_example.debugger.analysis

import com.runanywhere.kotlin_starter_example.debugger.input.CodeFile
import com.runanywhere.kotlin_starter_example.debugger.input.SupportedLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────
//  Error Models
// ─────────────────────────────────────────────

enum class ErrorType {
    SYNTAX_MISSING_BRACKET,
    SYNTAX_MISSING_PAREN,
    SYNTAX_MISSING_BRACE,
    SYNTAX_INVALID_TOKEN,
    SYNTAX_INVALID_STATEMENT,
    SYNTAX_UNCLOSED_STRING,
    SYNTAX_INDENTATION_ERROR,
    SEMANTIC_UNDEFINED_VARIABLE,
    SEMANTIC_UNDEFINED_FUNCTION,
    SEMANTIC_TYPE_MISMATCH,
    SEMANTIC_UNREACHABLE_CODE,
    SEMANTIC_SCOPE_VIOLATION,
    SEMANTIC_WRONG_ARGUMENT_COUNT,
    SEMANTIC_DUPLICATE_DECLARATION,
    SEMANTIC_MISSING_RETURN
}

enum class Severity { CRITICAL, HIGH, MEDIUM, LOW }

data class DebugError(
    val errorType: ErrorType,
    val severity: Severity,
    val lineNumber: Int,
    val columnNumber: Int = 0,
    val errorContext: String,       // Snippet of the offending line
    val message: String,
    val confidence: Float,          // 0.0 – 1.0
    val suggestion: String = ""
)

data class AnalysisReport(
    val file: CodeFile,
    val errors: List<DebugError>,
    val analysisTimeMs: Long,
    val summary: String
)

// ─────────────────────────────────────────────
//  Phase 2 — Core Debug Engine
// ─────────────────────────────────────────────

class CoreDebugEngine {

    suspend fun analyzeFile(file: CodeFile): AnalysisReport = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        val lines = file.normalizedContent.lines()

        val syntaxErrors = SyntaxAnalyzer.analyze(lines, file.language)
        val semanticErrors = SemanticAnalyzer.analyze(lines, file.language)

        val allErrors = (syntaxErrors + semanticErrors).sortedBy { it.lineNumber }
        val elapsed = System.currentTimeMillis() - start

        AnalysisReport(
            file = file,
            errors = allErrors,
            analysisTimeMs = elapsed,
            summary = buildSummary(allErrors, file.fileName)
        )
    }

    private fun buildSummary(errors: List<DebugError>, fileName: String): String {
        if (errors.isEmpty()) return "✅ No issues found in $fileName"
        val criticalCount = errors.count { it.severity == Severity.CRITICAL }
        val highCount = errors.count { it.severity == Severity.HIGH }
        val mediumCount = errors.count { it.severity == Severity.MEDIUM }
        return "Found ${errors.size} issue(s) in $fileName: " +
                "$criticalCount critical, $highCount high, $mediumCount medium"
    }
}

// ─────────────────────────────────────────────
//  2A — Syntax Analyzer
// ─────────────────────────────────────────────

object SyntaxAnalyzer {

    fun analyze(lines: List<String>, language: SupportedLanguage): List<DebugError> {
        val errors = mutableListOf<DebugError>()
        errors += checkBracketBalance(lines)
        errors += checkUnclosedStrings(lines, language)
        errors += checkInvalidTokens(lines, language)
        if (language == SupportedLanguage.PYTHON) {
            errors += checkPythonIndentation(lines)
        }
        return errors
    }

    // ── Bracket/Paren/Brace Balance ──────────────────────────────────────

    private fun checkBracketBalance(lines: List<String>): List<DebugError> {
        val errors = mutableListOf<DebugError>()
        val parenStack = ArrayDeque<Pair<Char, Int>>()  // char + line index
        val braceStack = ArrayDeque<Pair<Char, Int>>()
        val bracketStack = ArrayDeque<Pair<Char, Int>>()
        var inBlockComment = false
        var inLineComment = false

        lines.forEachIndexed { lineIdx, line ->
            inLineComment = false
            var i = 0
            var inString = false
            var stringChar = ' '

            while (i < line.length) {
                val c = line[i]
                val next = if (i + 1 < line.length) line[i + 1] else ' '

                // Block comment detection
                if (!inString && c == '/' && next == '*') { inBlockComment = true; i += 2; continue }
                if (inBlockComment && c == '*' && next == '/') { inBlockComment = false; i += 2; continue }
                if (inBlockComment) { i++; continue }

                // Line comment
                if (!inString && (c == '/' && next == '/' || c == '#' || c == ';')) break

                // String tracking
                if (!inString && (c == '"' || c == '\'')) { inString = true; stringChar = c; i++; continue }
                if (inString && c == '\\') { i += 2; continue }  // escape sequence
                if (inString && c == stringChar) { inString = false; i++; continue }
                if (inString) { i++; continue }

                when (c) {
                    '(' -> parenStack.addLast(Pair(c, lineIdx))
                    ')' -> {
                        if (parenStack.isEmpty()) {
                            errors += DebugError(
                                errorType = ErrorType.SYNTAX_MISSING_PAREN,
                                severity = Severity.CRITICAL,
                                lineNumber = lineIdx + 1,
                                columnNumber = i + 1,
                                errorContext = line.trim(),
                                message = "Unexpected closing parenthesis ')'",
                                confidence = 0.95f,
                                suggestion = "Check for extra ')' or missing '(' earlier in the code"
                            )
                        } else parenStack.removeLast()
                    }
                    '{' -> braceStack.addLast(Pair(c, lineIdx))
                    '}' -> {
                        if (braceStack.isEmpty()) {
                            errors += DebugError(
                                errorType = ErrorType.SYNTAX_MISSING_BRACE,
                                severity = Severity.CRITICAL,
                                lineNumber = lineIdx + 1,
                                columnNumber = i + 1,
                                errorContext = line.trim(),
                                message = "Unexpected closing brace '}'",
                                confidence = 0.95f,
                                suggestion = "Remove extra '}' or add matching '{'"
                            )
                        } else braceStack.removeLast()
                    }
                    '[' -> bracketStack.addLast(Pair(c, lineIdx))
                    ']' -> {
                        if (bracketStack.isEmpty()) {
                            errors += DebugError(
                                errorType = ErrorType.SYNTAX_MISSING_BRACKET,
                                severity = Severity.HIGH,
                                lineNumber = lineIdx + 1,
                                columnNumber = i + 1,
                                errorContext = line.trim(),
                                message = "Unexpected closing bracket ']'",
                                confidence = 0.90f,
                                suggestion = "Check for unmatched ']'"
                            )
                        } else bracketStack.removeLast()
                    }
                }
                i++
            }
        }

        // Unclosed openers
        parenStack.forEach { (_, lineIdx) ->
            errors += DebugError(
                errorType = ErrorType.SYNTAX_MISSING_PAREN,
                severity = Severity.CRITICAL,
                lineNumber = lineIdx + 1,
                errorContext = lines[lineIdx].trim(),
                message = "Unclosed parenthesis '(' has no matching ')'",
                confidence = 0.90f,
                suggestion = "Add closing ')' to match the opening parenthesis"
            )
        }
        braceStack.forEach { (_, lineIdx) ->
            errors += DebugError(
                errorType = ErrorType.SYNTAX_MISSING_BRACE,
                severity = Severity.CRITICAL,
                lineNumber = lineIdx + 1,
                errorContext = lines[lineIdx].trim(),
                message = "Unclosed brace '{' has no matching '}'",
                confidence = 0.90f,
                suggestion = "Add closing '}' to match the opening brace"
            )
        }
        bracketStack.forEach { (_, lineIdx) ->
            errors += DebugError(
                errorType = ErrorType.SYNTAX_MISSING_BRACKET,
                severity = Severity.HIGH,
                lineNumber = lineIdx + 1,
                errorContext = lines[lineIdx].trim(),
                message = "Unclosed bracket '[' has no matching ']'",
                confidence = 0.88f,
                suggestion = "Add closing ']' to match the opening bracket"
            )
        }

        return errors
    }

    // ── Unclosed String Literals ─────────────────────────────────────────

    private fun checkUnclosedStrings(lines: List<String>, language: SupportedLanguage): List<DebugError> {
        val errors = mutableListOf<DebugError>()
        lines.forEachIndexed { idx, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("//") || trimmed.startsWith("#")) return@forEachIndexed

            var inString = false
            var stringChar = ' '
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (!inString && (c == '"' || c == '\'')) {
                    inString = true; stringChar = c
                } else if (inString && c == '\\') {
                    i++ // skip escaped char
                } else if (inString && c == stringChar) {
                    inString = false
                }
                i++
            }
            if (inString) {
                errors += DebugError(
                    errorType = ErrorType.SYNTAX_UNCLOSED_STRING,
                    severity = Severity.CRITICAL,
                    lineNumber = idx + 1,
                    errorContext = line.trim(),
                    message = "Unclosed string literal (missing closing $stringChar)",
                    confidence = 0.85f,
                    suggestion = "Add closing $stringChar at the end of the string"
                )
            }
        }
        return errors
    }

    // ── Language-specific Invalid Tokens ────────────────────────────────

    private fun checkInvalidTokens(lines: List<String>, language: SupportedLanguage): List<DebugError> {
        val errors = mutableListOf<DebugError>()
        val rules: List<Pair<Regex, String>> = when (language) {
            SupportedLanguage.PYTHON -> listOf(
                Regex("\\bprint\\s+[^(]") to "Python 3: use print() with parentheses",
                Regex("\\bexcept\\s*,\\s*\\w") to "Python 3: use 'except Exception as e' syntax",
                Regex("\\bxrange\\b") to "xrange() removed in Python 3, use range()"
            )
            SupportedLanguage.JAVA -> listOf(
                Regex("=>") to "Java doesn't use '=>'; use '->' for lambda expressions",
                Regex("\\bvar\\b.*=.*\\bnew\\b") to "Check var usage — type must be inferable"
            )
            SupportedLanguage.KOTLIN -> listOf(
                Regex("\\bvoid\\b") to "Kotlin uses Unit instead of void",
                Regex("\\bnew\\s+\\w") to "Kotlin doesn't use 'new' keyword for object creation"
            )
            SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT -> listOf(
                Regex("===\\s*null\\s*&&\\s*===\\s*undefined") to "Use == null to check both null and undefined"
            )
            else -> emptyList()
        }
        lines.forEachIndexed { idx, line ->
            val stripped = line.trim()
            if (stripped.startsWith("//") || stripped.startsWith("#")) return@forEachIndexed
            rules.forEach { (regex, hint) ->
                if (regex.containsMatchIn(line)) {
                    errors += DebugError(
                        errorType = ErrorType.SYNTAX_INVALID_TOKEN,
                        severity = Severity.HIGH,
                        lineNumber = idx + 1,
                        errorContext = line.trim(),
                        message = "Invalid token/syntax: $hint",
                        confidence = 0.80f,
                        suggestion = hint
                    )
                }
            }
        }
        return errors
    }

    // ── Python Indentation ───────────────────────────────────────────────

    private fun checkPythonIndentation(lines: List<String>): List<DebugError> {
        val errors = mutableListOf<DebugError>()
        val blockKeywords = listOf("if ", "elif ", "else:", "for ", "while ", "def ", "class ", "try:", "except", "finally:", "with ")

        lines.forEachIndexed { idx, line ->
            if (idx == 0) return@forEachIndexed
            val prev = lines[idx - 1].trimEnd()
            if (blockKeywords.any { prev.trimStart().startsWith(it) } && prev.endsWith(":")) {
                val prevIndent = prev.length - prev.trimStart().length
                val currIndent = line.length - line.trimStart().length
                if (line.isNotBlank() && currIndent <= prevIndent) {
                    errors += DebugError(
                        errorType = ErrorType.SYNTAX_INDENTATION_ERROR,
                        severity = Severity.CRITICAL,
                        lineNumber = idx + 1,
                        errorContext = line.trim(),
                        message = "Expected indented block after '${prev.trim()}'",
                        confidence = 0.88f,
                        suggestion = "Indent this line by 4 spaces (or consistent indentation)"
                    )
                }
            }
        }
        return errors
    }
}

// ─────────────────────────────────────────────
//  2B — Semantic Analyzer
// ─────────────────────────────────────────────

object SemanticAnalyzer {

    fun analyze(lines: List<String>, language: SupportedLanguage): List<DebugError> {
        val errors = mutableListOf<DebugError>()
        errors += checkUndefinedVariables(lines, language)
        errors += checkUnreachableCode(lines, language)
        errors += checkDuplicateDeclarations(lines, language)
        errors += checkMissingReturn(lines, language)
        return errors
    }

    // ── Undefined Variable Heuristic ─────────────────────────────────────

    private fun checkUndefinedVariables(lines: List<String>, language: SupportedLanguage): List<DebugError> {
        val errors = mutableListOf<DebugError>()
        val declaredVars = mutableSetOf<String>()

        // Collect declarations based on language patterns
        val declPattern: Regex = when (language) {
            SupportedLanguage.PYTHON -> Regex("^\\s*(\\w+)\\s*=(?!=)")
            SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT ->
                Regex("\\b(?:var|let|const)\\s+(\\w+)")
            SupportedLanguage.KOTLIN -> Regex("\\b(?:val|var)\\s+(\\w+)")
            SupportedLanguage.JAVA -> Regex("\\b(?:int|String|boolean|double|float|long|char|\\w+)\\s+(\\w+)\\s*[=;(]")
            else -> Regex("\\b(\\w+)\\s*=(?!=)")
        }

        // First pass — collect all declared variables
        lines.forEach { line ->
            declPattern.findAll(line).forEach { match ->
                match.groupValues.getOrNull(1)?.let { name ->
                    if (name.isNotBlank() && name.length > 1) declaredVars.add(name)
                }
            }
        }

        // Also add common built-ins
        val builtins = when (language) {
            SupportedLanguage.PYTHON -> setOf("print", "len", "range", "input", "int", "str", "float",
                "list", "dict", "set", "tuple", "type", "isinstance", "True", "False", "None",
                "self", "cls", "super", "open", "enumerate", "zip", "map", "filter")
            SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT -> setOf("console", "document",
                "window", "Math", "Array", "Object", "String", "Number", "Boolean", "JSON",
                "Promise", "undefined", "null", "true", "false", "this", "arguments")
            SupportedLanguage.KOTLIN -> setOf("println", "print", "readLine", "listOf", "mapOf",
                "setOf", "arrayOf", "it", "this", "super", "true", "false", "null")
            SupportedLanguage.JAVA -> setOf("System", "String", "Integer", "Math", "Object",
                "null", "true", "false", "this", "super")
            else -> emptySet()
        }
        declaredVars.addAll(builtins)

        // Second pass — find usages not declared (simple heuristic)
        val usagePattern = Regex("\\b([a-zA-Z_]\\w{2,})\\b")
        val keywords = setOf("if", "else", "for", "while", "return", "import", "from", "class",
            "def", "fun", "val", "var", "let", "const", "void", "public", "private",
            "protected", "static", "new", "try", "catch", "throw", "throws", "final",
            "override", "data", "object", "companion", "package", "function")

        lines.forEachIndexed { idx, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("import")) return@forEachIndexed

            usagePattern.findAll(line).forEach { match ->
                val name = match.groupValues[1]
                if (name !in keywords && name !in declaredVars &&
                    !name[0].isUpperCase() &&  // skip class names
                    !line.contains("$name =") && !line.contains("$name:") &&
                    !line.contains("fun $name") && !line.contains("def $name") &&
                    !line.contains("val $name") && !line.contains("var $name")
                ) {
                    // Only flag if it looks like a variable usage (not a function definition)
                    if (line.contains("($name)") || line.contains(" $name ") ||
                        line.contains("$name.") || line.contains("[$name]")) {
                        errors += DebugError(
                            errorType = ErrorType.SEMANTIC_UNDEFINED_VARIABLE,
                            severity = Severity.HIGH,
                            lineNumber = idx + 1,
                            errorContext = line.trim(),
                            message = "Possible undefined variable: '$name'",
                            confidence = 0.60f,  // lower confidence — heuristic
                            suggestion = "Declare '$name' before use, or check for typo"
                        )
                        declaredVars.add(name)  // don't repeat for same var
                    }
                }
            }
        }
        return errors
    }

    // ── Unreachable Code ─────────────────────────────────────────────────

    private fun checkUnreachableCode(lines: List<String>, language: SupportedLanguage): List<DebugError> {
        val errors = mutableListOf<DebugError>()
        val returnKeywords = when (language) {
            SupportedLanguage.PYTHON -> listOf("return ", "return\n", "raise ", "sys.exit")
            SupportedLanguage.KOTLIN, SupportedLanguage.JAVA -> listOf("return ", "return;", "throw ", "System.exit")
            SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT -> listOf("return ", "return;", "throw ", "process.exit")
            else -> listOf("return ", "return;")
        }
        var lastReturnLine = -1
        var currentIndent = -1

        lines.forEachIndexed { idx, line ->
            val trimmed = line.trim()
            val indent = line.length - line.trimStart().length

            if (returnKeywords.any { trimmed.startsWith(it) || trimmed == it.trim() }) {
                lastReturnLine = idx
                currentIndent = indent
            } else if (lastReturnLine >= 0 && indent == currentIndent &&
                trimmed.isNotBlank() &&
                !trimmed.startsWith("//") && !trimmed.startsWith("#") &&
                trimmed != "}" && trimmed != "else:" && !trimmed.startsWith("else") &&
                !trimmed.startsWith("except") && !trimmed.startsWith("finally") &&
                !trimmed.startsWith("case") && !trimmed.startsWith("default")
            ) {
                errors += DebugError(
                    errorType = ErrorType.SEMANTIC_UNREACHABLE_CODE,
                    severity = Severity.MEDIUM,
                    lineNumber = idx + 1,
                    errorContext = trimmed,
                    message = "Unreachable code after return/throw statement at line ${lastReturnLine + 1}",
                    confidence = 0.75f,
                    suggestion = "Remove this code or restructure the control flow"
                )
                lastReturnLine = -1
            } else if (indent < currentIndent) {
                lastReturnLine = -1
            }
        }
        return errors
    }

    // ── Duplicate Declarations ───────────────────────────────────────────

    private fun checkDuplicateDeclarations(lines: List<String>, language: SupportedLanguage): List<DebugError> {
        val errors = mutableListOf<DebugError>()
        val declaredNames = mutableMapOf<String, Int>()  // name → first line

        val patterns: List<Regex> = when (language) {
            SupportedLanguage.KOTLIN -> listOf(
                Regex("\\b(?:val|var|fun)\\s+(\\w+)"),
                Regex("\\bclass\\s+(\\w+)")
            )
            SupportedLanguage.JAVA -> listOf(
                Regex("\\b(?:class|interface|enum)\\s+(\\w+)"),
                Regex("(?:int|String|boolean|double|float|long)\\s+(\\w+)\\s*[=;]")
            )
            SupportedLanguage.PYTHON -> listOf(
                Regex("^\\s*(?:def|class)\\s+(\\w+)"),
                Regex("^\\s*(\\w+)\\s*=(?!=)")
            )
            SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT -> listOf(
                Regex("\\b(?:function|class)\\s+(\\w+)"),
                Regex("\\b(?:const|let)\\s+(\\w+)")
            )
            else -> emptyList()
        }

        lines.forEachIndexed { idx, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("//") || trimmed.startsWith("#")) return@forEachIndexed
            patterns.forEach { pattern ->
                pattern.findAll(line).forEach { match ->
                    val name = match.groupValues.getOrNull(1) ?: return@forEach
                    if (name.isBlank() || name.length < 2) return@forEach
                    if (name in declaredNames) {
                        errors += DebugError(
                            errorType = ErrorType.SEMANTIC_DUPLICATE_DECLARATION,
                            severity = Severity.HIGH,
                            lineNumber = idx + 1,
                            errorContext = trimmed,
                            message = "'$name' already declared at line ${declaredNames[name]}",
                            confidence = 0.82f,
                            suggestion = "Rename one of the declarations or remove the duplicate"
                        )
                    } else {
                        declaredNames[name] = idx + 1
                    }
                }
            }
        }
        return errors
    }

    // ── Missing Return ────────────────────────────────────────────────────

    private fun checkMissingReturn(lines: List<String>, language: SupportedLanguage): List<DebugError> {
        val errors = mutableListOf<DebugError>()
        if (language !in listOf(SupportedLanguage.PYTHON, SupportedLanguage.JAVA,
                SupportedLanguage.KOTLIN, SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT)) {
            return errors
        }

        // Find functions that declare a non-void return type but don't seem to return
        val funcPattern: Regex = when (language) {
            SupportedLanguage.JAVA -> Regex("\\b(int|String|boolean|double|float|long|\\w+)\\s+\\w+\\s*\\(")
            SupportedLanguage.KOTLIN -> Regex("fun\\s+\\w+\\s*\\(.*\\)\\s*:\\s*(\\w+)")
            else -> return errors  // Heuristic too noisy for dynamic languages
        }

        var inFunction = false
        var funcReturnType = ""
        var funcStartLine = -1
        var braceDepth = 0
        var hasReturn = false

        lines.forEachIndexed { idx, line ->
            val match = funcPattern.find(line)
            if (match != null && line.contains("{")) {
                inFunction = true
                funcReturnType = match.groupValues[1]
                funcStartLine = idx + 1
                braceDepth = 1
                hasReturn = false
                return@forEachIndexed
            }
            if (inFunction) {
                braceDepth += line.count { it == '{' } - line.count { it == '}' }
                if (line.trim().startsWith("return")) hasReturn = true
                if (braceDepth <= 0) {
                    if (!hasReturn && funcReturnType !in listOf("void", "Unit", "Nothing")) {
                        errors += DebugError(
                            errorType = ErrorType.SEMANTIC_MISSING_RETURN,
                            severity = Severity.HIGH,
                            lineNumber = idx + 1,
                            errorContext = line.trim(),
                            message = "Function starting at line $funcStartLine with return type '$funcReturnType' may be missing a return statement",
                            confidence = 0.72f,
                            suggestion = "Add a return statement that returns type '$funcReturnType'"
                        )
                    }
                    inFunction = false
                }
            }
        }
        return errors
    }
}
