package com.runanywhere.kotlin_starter_example.debugger.quality

import com.runanywhere.kotlin_starter_example.debugger.input.CodeFile
import com.runanywhere.kotlin_starter_example.debugger.input.SupportedLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────
//  Quality Models
// ─────────────────────────────────────────────

enum class IssueCategory {
    COMPLEXITY,
    NAMING_CONVENTION,
    PERFORMANCE,
    DESIGN_PATTERN,
    DEAD_CODE,
    DOCUMENTATION,
    SECURITY,
    BEST_PRACTICE
}

enum class IssueSeverity(val score: Int, val label: String) {
    BLOCKER(5, "Blocker"),
    CRITICAL(4, "Critical"),
    MAJOR(3, "Major"),
    MINOR(2, "Minor"),
    INFO(1, "Info")
}

data class QualityIssue(
    val category: IssueCategory,
    val severity: IssueSeverity,
    val severityScore: Int,
    val file: String,
    val lineNumber: Int,
    val lineContext: String,
    val improvementHint: String,
    val ruleSource: String   // "PMD", "Pylint", "ESLint", "Custom"
)

data class QualityReport(
    val file: CodeFile,
    val issues: List<QualityIssue>,
    val overallScore: Int,          // 0–100 (higher = better)
    val complexityMetrics: ComplexityMetrics,
    val analysisTimeMs: Long
)

data class ComplexityMetrics(
    val cyclomaticComplexity: Int,
    val linesOfCode: Int,
    val commentRatio: Float,
    val avgFunctionLength: Int,
    val maxNestingDepth: Int
)

// ─────────────────────────────────────────────
//  Phase 3 — Quality Evaluation Layer
// ─────────────────────────────────────────────

class QualityEvaluator {

    suspend fun evaluateFile(file: CodeFile): QualityReport = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        val lines = file.normalizedContent.lines()

        val issues = mutableListOf<QualityIssue>()

        // Route to language-specific analyzers
        when (file.language) {
            SupportedLanguage.PYTHON -> issues += PylintEmulator.analyze(lines, file.fileName)
            SupportedLanguage.JAVA -> issues += PmdEmulator.analyze(lines, file.fileName)
            SupportedLanguage.JAVASCRIPT,
            SupportedLanguage.TYPESCRIPT -> issues += EslintEmulator.analyze(lines, file.fileName)
            SupportedLanguage.KOTLIN -> issues += KtlintEmulator.analyze(lines, file.fileName)
            else -> issues += GenericQualityAnalyzer.analyze(lines, file.fileName)
        }

        // Universal checks apply to all languages
        issues += UniversalQualityRules.analyze(lines, file.fileName, file.language)

        val aggregated = QualityAggregator.aggregate(issues)
        val metrics = ComplexityCalculator.calculate(lines, file.language)
        val score = QualityScorer.score(aggregated, metrics)
        val elapsed = System.currentTimeMillis() - start

        QualityReport(
            file = file,
            issues = aggregated,
            overallScore = score,
            complexityMetrics = metrics,
            analysisTimeMs = elapsed
        )
    }
}

// ─────────────────────────────────────────────
//  Pylint Emulator (Python)
// ─────────────────────────────────────────────

object PylintEmulator {

    fun analyze(lines: List<String>, fileName: String): List<QualityIssue> {
        val issues = mutableListOf<QualityIssue>()

        lines.forEachIndexed { idx, line ->
            val trimmed = line.trim()
            val lineNum = idx + 1

            // C0103 — invalid-name (variables should be snake_case, 2+ chars)
            val varMatch = Regex("^([A-Za-z_]\\w*)\\s*=(?!=)").find(trimmed)
            varMatch?.groupValues?.getOrNull(1)?.let { name ->
                if (name.length == 1 && name !in listOf("i", "j", "k", "n", "x", "y", "z", "_")) {
                    issues += QualityIssue(
                        category = IssueCategory.NAMING_CONVENTION,
                        severity = IssueSeverity.MINOR,
                        severityScore = IssueSeverity.MINOR.score,
                        file = fileName, lineNumber = lineNum, lineContext = trimmed,
                        improvementHint = "Variable name '$name' is too short. Use descriptive names (C0103)",
                        ruleSource = "Pylint"
                    )
                }
                if (Regex("[A-Z]").containsMatchIn(name) && Regex("[a-z]").containsMatchIn(name) &&
                    name.contains(Regex("[A-Z]")) && !name.startsWith("_")) {
                    // camelCase variable — Python uses snake_case
                    if (name.any { it.isUpperCase() } && name[0].isLowerCase()) {
                        issues += QualityIssue(
                            category = IssueCategory.NAMING_CONVENTION,
                            severity = IssueSeverity.MINOR,
                            severityScore = IssueSeverity.MINOR.score,
                            file = fileName, lineNumber = lineNum, lineContext = trimmed,
                            improvementHint = "Use snake_case for variable '$name' instead of camelCase (C0103)",
                            ruleSource = "Pylint"
                        )
                    }
                }
            }

            // W0611 — unused-import (basic heuristic)
            if (trimmed.startsWith("import ") || trimmed.startsWith("from ")) {
                val importedName = trimmed.substringAfterLast(" ").trim()
                val usedElsewhere = lines.drop(idx + 1).any { it.contains(importedName) }
                if (!usedElsewhere && importedName.isNotBlank() && importedName.length > 2) {
                    issues += QualityIssue(
                        category = IssueCategory.DEAD_CODE,
                        severity = IssueSeverity.MINOR,
                        severityScore = IssueSeverity.MINOR.score,
                        file = fileName, lineNumber = lineNum, lineContext = trimmed,
                        improvementHint = "Possibly unused import '$importedName'. Remove if not needed (W0611)",
                        ruleSource = "Pylint"
                    )
                }
            }

            // W0102 — dangerous-default-value
            if (Regex("def\\s+\\w+\\(.*=\\s*\\[\\]").containsMatchIn(trimmed) ||
                Regex("def\\s+\\w+\\(.*=\\s*\\{\\}").containsMatchIn(trimmed)) {
                issues += QualityIssue(
                    category = IssueCategory.BEST_PRACTICE,
                    severity = IssueSeverity.MAJOR,
                    severityScore = IssueSeverity.MAJOR.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Don't use mutable default arguments ([] or {}). Use None and initialize inside function (W0102)",
                    ruleSource = "Pylint"
                )
            }

            // C0301 — line-too-long
            if (line.length > 120) {
                issues += QualityIssue(
                    category = IssueCategory.BEST_PRACTICE,
                    severity = IssueSeverity.INFO,
                    severityScore = IssueSeverity.INFO.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Line too long (${line.length}/120). Break into multiple lines (C0301)",
                    ruleSource = "Pylint"
                )
            }

            // W0612 — unused-variable (assigned but never used below)
            val assignMatch = Regex("^\\s*(\\w+)\\s*=(?!=)").find(line)
            assignMatch?.groupValues?.getOrNull(1)?.let { varName ->
                if (varName.startsWith("_")) return@let  // _ prefix = intentionally unused
                val usedBelow = lines.drop(idx + 1).any { it.contains(varName) }
                if (!usedBelow && varName.length > 2) {
                    issues += QualityIssue(
                        category = IssueCategory.DEAD_CODE,
                        severity = IssueSeverity.MINOR,
                        severityScore = IssueSeverity.MINOR.score,
                        file = fileName, lineNumber = lineNum, lineContext = trimmed,
                        improvementHint = "Variable '$varName' is assigned but may never be used (W0612). Prefix with _ if intentional",
                        ruleSource = "Pylint"
                    )
                }
            }

            // R1705 — no-else-return
            if (trimmed == "else:" && idx > 0) {
                val prevBlock = lines.subList(maxOf(0, idx - 5), idx)
                if (prevBlock.any { it.trim().startsWith("return") }) {
                    issues += QualityIssue(
                        category = IssueCategory.DESIGN_PATTERN,
                        severity = IssueSeverity.MINOR,
                        severityScore = IssueSeverity.MINOR.score,
                        file = fileName, lineNumber = lineNum, lineContext = trimmed,
                        improvementHint = "Unnecessary else after return. Remove the else block (R1705)",
                        ruleSource = "Pylint"
                    )
                }
            }
        }
        return issues
    }
}

// ─────────────────────────────────────────────
//  PMD Emulator (Java)
// ─────────────────────────────────────────────

object PmdEmulator {

    fun analyze(lines: List<String>, fileName: String): List<QualityIssue> {
        val issues = mutableListOf<QualityIssue>()

        lines.forEachIndexed { idx, line ->
            val trimmed = line.trim()
            val lineNum = idx + 1

            // DesignRules — GodClass (too many fields heuristic)
            // Checked at file level via field count — done in UniversalRules

            // UnusedLocalVariable
            val localVarMatch = Regex("\\b(?:int|String|boolean|double|float|long|char)\\s+(\\w+)\\s*=").find(trimmed)
            localVarMatch?.groupValues?.getOrNull(1)?.let { varName ->
                val usedBelow = lines.drop(idx + 1).any { it.contains(varName) }
                if (!usedBelow) {
                    issues += QualityIssue(
                        category = IssueCategory.DEAD_CODE,
                        severity = IssueSeverity.MINOR,
                        severityScore = IssueSeverity.MINOR.score,
                        file = fileName, lineNumber = lineNum, lineContext = trimmed,
                        improvementHint = "Local variable '$varName' is assigned but never used (PMD: UnusedLocalVariable)",
                        ruleSource = "PMD"
                    )
                }
            }

            // SystemPrintln
            if (trimmed.contains("System.out.print") || trimmed.contains("System.err.print")) {
                issues += QualityIssue(
                    category = IssueCategory.BEST_PRACTICE,
                    severity = IssueSeverity.MINOR,
                    severityScore = IssueSeverity.MINOR.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Avoid System.out/err.println in production. Use a proper logging framework (PMD: SystemPrintln)",
                    ruleSource = "PMD"
                )
            }

            // EmptyCatchBlock
            if (trimmed.startsWith("catch") && idx + 1 < lines.size) {
                val nextLine = lines[idx + 1].trim()
                if (nextLine == "}") {
                    issues += QualityIssue(
                        category = IssueCategory.BEST_PRACTICE,
                        severity = IssueSeverity.MAJOR,
                        severityScore = IssueSeverity.MAJOR.score,
                        file = fileName, lineNumber = lineNum, lineContext = trimmed,
                        improvementHint = "Empty catch block swallows exception silently. Log or handle it (PMD: EmptyCatchBlock)",
                        ruleSource = "PMD"
                    )
                }
            }

            // StringInstantiation
            if (Regex("new\\s+String\\s*\\(\"").containsMatchIn(trimmed)) {
                issues += QualityIssue(
                    category = IssueCategory.PERFORMANCE,
                    severity = IssueSeverity.MINOR,
                    severityScore = IssueSeverity.MINOR.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Avoid new String(\"...\"); use string literals directly (PMD: StringInstantiation)",
                    ruleSource = "PMD"
                )
            }

            // UseStringBufferForStringAppends
            if (Regex("\\w+\\s*\\+=\\s*\"").containsMatchIn(trimmed)) {
                issues += QualityIssue(
                    category = IssueCategory.PERFORMANCE,
                    severity = IssueSeverity.MAJOR,
                    severityScore = IssueSeverity.MAJOR.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "String concatenation in loop is inefficient. Use StringBuilder (PMD: UseStringBufferForStringAppends)",
                    ruleSource = "PMD"
                )
            }

            // NullAssignment check
            if (Regex("\\w+\\s*=\\s*null;").containsMatchIn(trimmed) &&
                !trimmed.contains("==") && !trimmed.contains("!=")) {
                issues += QualityIssue(
                    category = IssueCategory.DESIGN_PATTERN,
                    severity = IssueSeverity.MINOR,
                    severityScore = IssueSeverity.MINOR.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Assigning null could lead to NullPointerException later. Consider Optional<> (PMD: NullAssignment)",
                    ruleSource = "PMD"
                )
            }
        }
        return issues
    }
}

// ─────────────────────────────────────────────
//  ESLint Emulator (JavaScript/TypeScript)
// ─────────────────────────────────────────────

object EslintEmulator {

    fun analyze(lines: List<String>, fileName: String): List<QualityIssue> {
        val issues = mutableListOf<QualityIssue>()

        lines.forEachIndexed { idx, line ->
            val trimmed = line.trim()
            val lineNum = idx + 1

            // no-var
            if (Regex("\\bvar\\s+\\w+").containsMatchIn(trimmed)) {
                issues += QualityIssue(
                    category = IssueCategory.BEST_PRACTICE,
                    severity = IssueSeverity.MAJOR,
                    severityScore = IssueSeverity.MAJOR.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Use 'const' or 'let' instead of 'var' (ESLint: no-var)",
                    ruleSource = "ESLint"
                )
            }

            // eqeqeq — require === instead of ==
            if (Regex("(?<!=)[^!]={2}(?!=)").containsMatchIn(trimmed) &&
                !trimmed.contains("===") && !trimmed.contains("!==")) {
                issues += QualityIssue(
                    category = IssueCategory.BEST_PRACTICE,
                    severity = IssueSeverity.MAJOR,
                    severityScore = IssueSeverity.MAJOR.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Use '===' (strict equality) instead of '==' to avoid type coercion (ESLint: eqeqeq)",
                    ruleSource = "ESLint"
                )
            }

            // no-console
            if (trimmed.contains("console.log") || trimmed.contains("console.warn") ||
                trimmed.contains("console.error")) {
                issues += QualityIssue(
                    category = IssueCategory.BEST_PRACTICE,
                    severity = IssueSeverity.MINOR,
                    severityScore = IssueSeverity.MINOR.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Remove console statements before production (ESLint: no-console)",
                    ruleSource = "ESLint"
                )
            }

            // prefer-const — declared with let but not reassigned (heuristic)
            val letMatch = Regex("\\blet\\s+(\\w+)\\s*=").find(trimmed)
            letMatch?.groupValues?.getOrNull(1)?.let { varName ->
                val reassigned = lines.drop(idx + 1).any {
                    it.contains("$varName =") && !it.contains("==")
                }
                if (!reassigned) {
                    issues += QualityIssue(
                        category = IssueCategory.BEST_PRACTICE,
                        severity = IssueSeverity.MINOR,
                        severityScore = IssueSeverity.MINOR.score,
                        file = fileName, lineNumber = lineNum, lineContext = trimmed,
                        improvementHint = "Variable '$varName' is never reassigned. Use 'const' instead of 'let' (ESLint: prefer-const)",
                        ruleSource = "ESLint"
                    )
                }
            }

            // no-eval
            if (trimmed.contains("eval(")) {
                issues += QualityIssue(
                    category = IssueCategory.SECURITY,
                    severity = IssueSeverity.BLOCKER,
                    severityScore = IssueSeverity.BLOCKER.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "eval() is dangerous and can lead to XSS/code injection. Avoid it (ESLint: no-eval)",
                    ruleSource = "ESLint"
                )
            }

            // no-unused-vars (simple heuristic)
            val constMatch = Regex("\\bconst\\s+(\\w+)\\s*=").find(trimmed)
            constMatch?.groupValues?.getOrNull(1)?.let { varName ->
                val usedBelow = lines.drop(idx + 1).any { it.contains(varName) }
                val usedAbove = lines.take(idx).any { it.contains(varName) && !it.contains("const $varName") }
                if (!usedBelow && !usedAbove) {
                    issues += QualityIssue(
                        category = IssueCategory.DEAD_CODE,
                        severity = IssueSeverity.MINOR,
                        severityScore = IssueSeverity.MINOR.score,
                        file = fileName, lineNumber = lineNum, lineContext = trimmed,
                        improvementHint = "'$varName' is defined but may never be used (ESLint: no-unused-vars)",
                        ruleSource = "ESLint"
                    )
                }
            }
        }
        return issues
    }
}

// ─────────────────────────────────────────────
//  Ktlint Emulator (Kotlin)
// ─────────────────────────────────────────────

object KtlintEmulator {

    fun analyze(lines: List<String>, fileName: String): List<QualityIssue> {
        val issues = mutableListOf<QualityIssue>()

        lines.forEachIndexed { idx, line ->
            val trimmed = line.trim()
            val lineNum = idx + 1

            // Trailing whitespace
            if (line.endsWith(" ") || line.endsWith("\t")) {
                issues += QualityIssue(
                    category = IssueCategory.BEST_PRACTICE,
                    severity = IssueSeverity.INFO,
                    severityScore = IssueSeverity.INFO.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Trailing whitespace detected (ktlint: no-trailing-spaces)",
                    ruleSource = "Ktlint"
                )
            }

            // Wildcard imports
            if (trimmed.startsWith("import") && trimmed.endsWith(".*")) {
                issues += QualityIssue(
                    category = IssueCategory.BEST_PRACTICE,
                    severity = IssueSeverity.MINOR,
                    severityScore = IssueSeverity.MINOR.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Avoid wildcard imports. Import specific classes (ktlint: no-wildcard-imports)",
                    ruleSource = "Ktlint"
                )
            }

            // Magic numbers
            if (Regex("(?<![.\"])\\b(?!0\\b|1\\b|2\\b)\\d{2,}\\b(?!\")").containsMatchIn(trimmed) &&
                !trimmed.startsWith("//") && !trimmed.contains("const val")) {
                issues += QualityIssue(
                    category = IssueCategory.DESIGN_PATTERN,
                    severity = IssueSeverity.MINOR,
                    severityScore = IssueSeverity.MINOR.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Avoid magic numbers. Extract to named constants (e.g., const val MAX_RETRY = 3)",
                    ruleSource = "Ktlint"
                )
            }

            // Force unwrap (!!)
            if (trimmed.contains("!!")) {
                issues += QualityIssue(
                    category = IssueCategory.BEST_PRACTICE,
                    severity = IssueSeverity.MAJOR,
                    severityScore = IssueSeverity.MAJOR.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Avoid !! (non-null assertion). Use safe calls (?.), let {}, or Elvis operator ?: instead",
                    ruleSource = "Ktlint"
                )
            }

            // print instead of Log
            if (trimmed.contains("println(") && !fileName.contains("Test")) {
                issues += QualityIssue(
                    category = IssueCategory.BEST_PRACTICE,
                    severity = IssueSeverity.MINOR,
                    severityScore = IssueSeverity.MINOR.score,
                    file = fileName, lineNumber = lineNum, lineContext = trimmed,
                    improvementHint = "Use Android Log.d/i/e instead of println() in Android code",
                    ruleSource = "Ktlint"
                )
            }
        }
        return issues
    }
}

// ─────────────────────────────────────────────
//  Universal Quality Rules (all languages)
// ─────────────────────────────────────────────

object UniversalQualityRules {

    fun analyze(lines: List<String>, fileName: String, language: SupportedLanguage): List<QualityIssue> {
        val issues = mutableListOf<QualityIssue>()
        issues += checkLineTooLong(lines, fileName)
        issues += checkTodoComments(lines, fileName)
        issues += checkPasswordHardcoded(lines, fileName)
        issues += checkLargeFunction(lines, fileName, language)
        return issues
    }

    private fun checkLineTooLong(lines: List<String>, fileName: String): List<QualityIssue> {
        return lines.mapIndexedNotNull { idx, line ->
            if (line.length > 150) {
                QualityIssue(
                    category = IssueCategory.BEST_PRACTICE,
                    severity = IssueSeverity.INFO,
                    severityScore = IssueSeverity.INFO.score,
                    file = fileName, lineNumber = idx + 1, lineContext = line.take(80) + "...",
                    improvementHint = "Line is ${line.length} characters long. Keep lines under 120 for readability",
                    ruleSource = "Custom"
                )
            } else null
        }
    }

    private fun checkTodoComments(lines: List<String>, fileName: String): List<QualityIssue> {
        return lines.mapIndexedNotNull { idx, line ->
            val trimmed = line.trim()
            if (Regex("//\\s*TODO|#\\s*TODO|//\\s*FIXME|#\\s*FIXME|//\\s*HACK", RegexOption.IGNORE_CASE)
                    .containsMatchIn(trimmed)) {
                QualityIssue(
                    category = IssueCategory.BEST_PRACTICE,
                    severity = IssueSeverity.INFO,
                    severityScore = IssueSeverity.INFO.score,
                    file = fileName, lineNumber = idx + 1, lineContext = trimmed,
                    improvementHint = "TODO/FIXME comment found. Track this in your issue tracker and resolve it",
                    ruleSource = "Custom"
                )
            } else null
        }
    }

    private fun checkPasswordHardcoded(lines: List<String>, fileName: String): List<QualityIssue> {
        val sensitivePatterns = listOf("password", "passwd", "secret", "api_key", "apikey", "token", "auth")
        return lines.mapIndexedNotNull { idx, line ->
            val lower = line.lowercase()
            val hasSensitiveWord = sensitivePatterns.any { lower.contains(it) }
            val hasAssignment = line.contains("=") && (line.contains("\"") || line.contains("'"))
            if (hasSensitiveWord && hasAssignment && !line.trim().startsWith("//") && !line.trim().startsWith("#")) {
                QualityIssue(
                    category = IssueCategory.SECURITY,
                    severity = IssueSeverity.BLOCKER,
                    severityScore = IssueSeverity.BLOCKER.score,
                    file = fileName, lineNumber = idx + 1, lineContext = line.trim(),
                    improvementHint = "⚠️ Possible hardcoded credential detected. Use environment variables or a secrets manager",
                    ruleSource = "Custom"
                )
            } else null
        }
    }

    private fun checkLargeFunction(lines: List<String>, fileName: String, language: SupportedLanguage): List<QualityIssue> {
        val issues = mutableListOf<QualityIssue>()
        val funcStartPattern = when (language) {
            SupportedLanguage.PYTHON -> Regex("^\\s*def\\s+\\w+")
            SupportedLanguage.KOTLIN -> Regex("\\bfun\\s+\\w+")
            SupportedLanguage.JAVA -> Regex("\\b(?:public|private|protected|static)?\\s*\\w+\\s+\\w+\\s*\\(")
            SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT ->
                Regex("\\b(?:function|=>)\\s*\\w*\\s*\\(")
            else -> return issues
        }

        var funcStart = -1
        var braceDepth = 0

        lines.forEachIndexed { idx, line ->
            if (funcStartPattern.containsMatchIn(line)) {
                funcStart = idx
                braceDepth = line.count { it == '{' }
            } else if (funcStart >= 0) {
                braceDepth += line.count { it == '{' } - line.count { it == '}' }
                if (braceDepth <= 0) {
                    val funcLen = idx - funcStart
                    if (funcLen > 50) {
                        issues += QualityIssue(
                            category = IssueCategory.COMPLEXITY,
                            severity = IssueSeverity.MAJOR,
                            severityScore = IssueSeverity.MAJOR.score,
                            file = fileName, lineNumber = funcStart + 1,
                            lineContext = lines[funcStart].trim(),
                            improvementHint = "Function is $funcLen lines long (at line ${funcStart + 1}). Functions > 50 lines should be refactored into smaller units",
                            ruleSource = "Custom"
                        )
                    }
                    funcStart = -1
                }
            }
        }
        return issues
    }
}

object GenericQualityAnalyzer {
    fun analyze(lines: List<String>, fileName: String): List<QualityIssue> =
        UniversalQualityRules.analyze(lines, fileName, SupportedLanguage.UNKNOWN)
}

// ─────────────────────────────────────────────
//  Quality Aggregator — Normalize all outputs
// ─────────────────────────────────────────────

object QualityAggregator {

    fun aggregate(issues: List<QualityIssue>): List<QualityIssue> {
        // Deduplicate: same line + same category = same issue
        val seen = mutableSetOf<String>()
        return issues
            .filter { issue ->
                val key = "${issue.file}:${issue.lineNumber}:${issue.category}"
                seen.add(key)
            }
            .sortedWith(compareByDescending<QualityIssue> { it.severityScore }.thenBy { it.lineNumber })
    }
}

// ─────────────────────────────────────────────
//  Complexity Calculator
// ─────────────────────────────────────────────

object ComplexityCalculator {

    fun calculate(lines: List<String>, language: SupportedLanguage): ComplexityMetrics {
        val nonBlank = lines.filter { it.isNotBlank() }
        val commentLines = nonBlank.count { isComment(it, language) }
        val codeLines = nonBlank.size - commentLines

        val cyclomaticComplexity = calculateCyclomaticComplexity(lines)
        val maxNesting = calculateMaxNesting(lines)
        val avgFuncLen = calculateAvgFunctionLength(lines, language)

        return ComplexityMetrics(
            cyclomaticComplexity = cyclomaticComplexity,
            linesOfCode = codeLines,
            commentRatio = if (nonBlank.isEmpty()) 0f else commentLines.toFloat() / nonBlank.size,
            avgFunctionLength = avgFuncLen,
            maxNestingDepth = maxNesting
        )
    }

    private fun isComment(line: String, language: SupportedLanguage): Boolean {
        val t = line.trim()
        return when (language) {
            SupportedLanguage.PYTHON -> t.startsWith("#")
            SupportedLanguage.JAVA, SupportedLanguage.KOTLIN,
            SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT ->
                t.startsWith("//") || t.startsWith("/*") || t.startsWith("*")
            else -> t.startsWith("//") || t.startsWith("#")
        }
    }

    private fun calculateCyclomaticComplexity(lines: List<String>): Int {
        val branchKeywords = listOf("if ", "else if", "elif ", "for ", "while ", "case ", "catch ", "&&", "||", "?:")
        var complexity = 1
        lines.forEach { line ->
            branchKeywords.forEach { kw -> if (line.contains(kw)) complexity++ }
        }
        return complexity
    }

    private fun calculateMaxNesting(lines: List<String>): Int {
        var maxDepth = 0
        var currentDepth = 0
        lines.forEach { line ->
            currentDepth += line.count { it == '{' } - line.count { it == '}' }
            if (currentDepth > maxDepth) maxDepth = currentDepth
        }
        return maxDepth
    }

    private fun calculateAvgFunctionLength(lines: List<String>, language: SupportedLanguage): Int {
        val funcLengths = mutableListOf<Int>()
        var start = -1
        var depth = 0
        lines.forEachIndexed { idx, line ->
            val hasFuncKeyword = line.contains("def ") || line.contains("fun ") ||
                    line.contains("function ") || (line.contains("{") && idx > 0 &&
                    lines[idx - 1].contains(Regex("\\w+\\s*\\(")))
            if (hasFuncKeyword && start == -1) start = idx
            if (start >= 0) {
                depth += line.count { it == '{' } - line.count { it == '}' }
                if (depth <= 0 && start >= 0) {
                    funcLengths.add(idx - start)
                    start = -1
                    depth = 0
                }
            }
        }
        return if (funcLengths.isEmpty()) 0 else funcLengths.average().toInt()
    }
}

// ─────────────────────────────────────────────
//  Quality Scorer — 0 to 100
// ─────────────────────────────────────────────

object QualityScorer {

    fun score(issues: List<QualityIssue>, metrics: ComplexityMetrics): Int {
        var score = 100

        // Deduct based on severity
        issues.forEach { issue ->
            score -= when (issue.severity) {
                IssueSeverity.BLOCKER -> 20
                IssueSeverity.CRITICAL -> 10
                IssueSeverity.MAJOR -> 5
                IssueSeverity.MINOR -> 2
                IssueSeverity.INFO -> 0
            }
        }

        // Deduct for high cyclomatic complexity
        if (metrics.cyclomaticComplexity > 20) score -= 10
        else if (metrics.cyclomaticComplexity > 10) score -= 5

        // Deduct for deep nesting
        if (metrics.maxNestingDepth > 5) score -= 10
        else if (metrics.maxNestingDepth > 3) score -= 5

        // Deduct for very long functions
        if (metrics.avgFunctionLength > 100) score -= 10
        else if (metrics.avgFunctionLength > 50) score -= 5

        // Reward good comment ratio
        if (metrics.commentRatio > 0.15f) score += 5

        return score.coerceIn(0, 100)
    }
}
