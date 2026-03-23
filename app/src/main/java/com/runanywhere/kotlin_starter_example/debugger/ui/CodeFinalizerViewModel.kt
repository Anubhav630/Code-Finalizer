//package com.runanywhere.kotlin_starter_example.debugger.ui
//
//import android.content.Context
//import android.graphics.Bitmap
//import android.net.Uri
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.runanywhere.kotlin_starter_example.debugger.analysis.AnalysisReport
//import com.runanywhere.kotlin_starter_example.debugger.analysis.DebugError
//import com.runanywhere.kotlin_starter_example.debugger.input.CodeFile
//import com.runanywhere.kotlin_starter_example.debugger.input.CodeInputProcessor
//import com.runanywhere.kotlin_starter_example.debugger.input.InputResult
//import com.runanywhere.kotlin_starter_example.debugger.input.SupportedLanguage
//import com.runanywhere.kotlin_starter_example.debugger.llm.CodeFinalizerPipeline
//import com.runanywhere.kotlin_starter_example.debugger.llm.FullDebugSession
//import com.runanywhere.kotlin_starter_example.debugger.llm.LLMSolutionGenerator
//import com.runanywhere.kotlin_starter_example.debugger.quality.QualityReport
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.flow.update
//import kotlinx.coroutines.launch
//
//// ─────────────────────────────────────────────
////  UI State
//// ─────────────────────────────────────────────
//
//sealed class DebugPhase {
//    object Idle : DebugPhase()
//    object ProcessingInput : DebugPhase()     // Phase 1
//    object AnalyzingCode : DebugPhase()       // Phase 2
//    object EvaluatingQuality : DebugPhase()   // Phase 3
//    object GeneratingSolution : DebugPhase()  // LLM
//    data class Complete(val sessions: List<FullDebugSession>) : DebugPhase()
//    data class Error(val message: String) : DebugPhase()
//}
//
//data class CodeFinalizerState(
//    val phase: DebugPhase = DebugPhase.Idle,
//    val loadedFiles: List<CodeFile> = emptyList(),
//    val selectedFileIndex: Int = 0,
//    val currentSession: FullDebugSession? = null,
//    val allSessions: List<FullDebugSession> = emptyList(),
//    val streamingOutput: String = "",
//    val isStreaming: Boolean = false,
//    val userQuestion: String = ""
//)
//
//// ─────────────────────────────────────────────
////  ViewModel
//// ─────────────────────────────────────────────
//
//class CodeFinalizerViewModel : ViewModel() {
//
//    private val _state = MutableStateFlow(CodeFinalizerState())
//    val state: StateFlow<CodeFinalizerState> = _state.asStateFlow()
//
//    private val pipeline = CodeFinalizerPipeline()
//    private val llmGenerator = LLMSolutionGenerator()
//
//    // ── Input Handlers ────────────────────────────────────────────────────
//
//    fun processZipFile(context: Context, uri: Uri) {
//        viewModelScope.launch {
//            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }
//            val processor = CodeInputProcessor(context)
//            when (val result = processor.processZip(uri)) {
//                is InputResult.Success -> onFilesLoaded(result.files)
//                is InputResult.Error -> _state.update {
//                    it.copy(phase = DebugPhase.Error(result.message))
//                }
//            }
//        }
//    }
//
//    fun processImage(context: Context, bitmap: Bitmap) {
//        viewModelScope.launch {
//            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }
//            val processor = CodeInputProcessor(context)
//            when (val result = processor.processImage(bitmap)) {
//                is InputResult.Success -> onFilesLoaded(result.files)
//                is InputResult.Error -> _state.update {
//                    it.copy(phase = DebugPhase.Error(result.message))
//                }
//            }
//        }
//    }
//
//    fun processTextInput(context: Context, code: String, language: SupportedLanguage? = null) {
//        viewModelScope.launch {
//            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }
//            val processor = CodeInputProcessor(context)
//            when (val result = processor.processText(code, language)) {
//                is InputResult.Success -> onFilesLoaded(result.files)
//                is InputResult.Error -> _state.update {
//                    it.copy(phase = DebugPhase.Error(result.message))
//                }
//            }
//        }
//    }
//
//    // ── Pipeline Execution ───────────────────────────────────────────────
//
//    private suspend fun onFilesLoaded(files: List<CodeFile>) {
//        _state.update { it.copy(loadedFiles = files) }
//
//        // Phase 2 — Analysis
//        _state.update { it.copy(phase = DebugPhase.AnalyzingCode) }
//
//        // Phase 3 — Quality
//        _state.update { it.copy(phase = DebugPhase.EvaluatingQuality) }
//
//        // Run full pipeline on all files
//        _state.update { it.copy(phase = DebugPhase.GeneratingSolution) }
//        val sessions = pipeline.runOnProject(files)
//
//        // Run LLM on the worst file (lowest quality score)
//        val worstSession = sessions.minByOrNull { it.qualityReport.overallScore }
//        val finalSessions = if (worstSession != null) {
//            val fullSession = pipeline.runFullPipeline(worstSession.codeFile, generateLLMSolution = true)
//            sessions.map { if (it.codeFile.fileName == fullSession.codeFile.fileName) fullSession else it }
//        } else sessions
//
//        _state.update {
//            it.copy(
//                phase = DebugPhase.Complete(finalSessions),
//                allSessions = finalSessions,
//                currentSession = finalSessions.firstOrNull()
//            )
//        }
//    }
//
//    fun runPipelineOnFile(index: Int) {
//        val file = _state.value.loadedFiles.getOrNull(index) ?: return
//        viewModelScope.launch {
//            _state.update { it.copy(phase = DebugPhase.AnalyzingCode, selectedFileIndex = index) }
//            val session = pipeline.runFullPipeline(file, generateLLMSolution = true)
//            _state.update { state ->
//                val updated = state.allSessions.toMutableList()
//                val existingIdx = updated.indexOfFirst { it.codeFile.fileName == file.fileName }
//                if (existingIdx >= 0) updated[existingIdx] = session else updated.add(session)
//                state.copy(
//                    phase = DebugPhase.Complete(updated),
//                    allSessions = updated,
//                    currentSession = session
//                )
//            }
//        }
//    }
//
//    fun askQuestion(question: String) {
//        val file = _state.value.currentSession?.codeFile ?: return
//        viewModelScope.launch {
//            _state.update { it.copy(isStreaming = true, streamingOutput = "", userQuestion = question) }
//            llmGenerator.streamDebugSolution(
//                file,
//                _state.value.currentSession!!.analysisReport
//            ).collect { chunk ->
//                _state.update { it.copy(streamingOutput = it.streamingOutput + chunk) }
//            }
//            _state.update { it.copy(isStreaming = false) }
//        }
//    }
//
//    fun selectFile(index: Int) {
//        val session = _state.value.allSessions.getOrNull(index)
//        _state.update { it.copy(selectedFileIndex = index, currentSession = session) }
//    }
//
//    fun reset() {
//        _state.update { CodeFinalizerState() }
//    }
//}
package com.runanywhere.kotlin_starter_example.debugger.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.kotlin_starter_example.debugger.analysis.AnalysisReport
import com.runanywhere.kotlin_starter_example.debugger.analysis.CoreDebugEngine
import com.runanywhere.kotlin_starter_example.debugger.input.CodeFile
import com.runanywhere.kotlin_starter_example.debugger.input.CodeInputProcessor
import com.runanywhere.kotlin_starter_example.debugger.input.InputResult
import com.runanywhere.kotlin_starter_example.debugger.input.SupportedLanguage
import com.runanywhere.kotlin_starter_example.debugger.quality.QualityEvaluator
import com.runanywhere.kotlin_starter_example.debugger.quality.QualityReport
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DebugChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class DebugPhase {
    object Idle : DebugPhase()
    object ProcessingInput : DebugPhase()
    object AnalyzingCode : DebugPhase()
    object EvaluatingQuality : DebugPhase()
    object Complete : DebugPhase()
    data class Error(val message: String) : DebugPhase()
}

data class CodeFinalizerState(
    val phase: DebugPhase = DebugPhase.Idle,
    val loadedFiles: List<CodeFile> = emptyList(),
    val selectedFileIndex: Int = 0,
    val analysisReports: Map<String, AnalysisReport> = emptyMap(),
    val qualityReports: Map<String, QualityReport> = emptyMap(),
    val chatMessages: List<DebugChatMessage> = emptyList(),
    val isGeneratingChat: Boolean = false,
    val chatInput: String = ""
)

class CodeFinalizerViewModel : ViewModel() {

    private val _state = MutableStateFlow(CodeFinalizerState())
    val state: StateFlow<CodeFinalizerState> = _state.asStateFlow()

    private val debugEngine = CoreDebugEngine()
    private val qualityEvaluator = QualityEvaluator()

    val selectedFile: CodeFile?
        get() = _state.value.loadedFiles.getOrNull(_state.value.selectedFileIndex)

    val selectedAnalysis: AnalysisReport?
        get() = selectedFile?.let { _state.value.analysisReports[it.fileName] }

    val selectedQuality: QualityReport?
        get() = selectedFile?.let { _state.value.qualityReports[it.fileName] }

    fun processZip(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }
            val processor = CodeInputProcessor(context)
            when (val result = processor.processZip(uri)) {
                is InputResult.Success -> runAnalysis(result.files)
                is InputResult.Error -> _state.update { it.copy(phase = DebugPhase.Error(result.message)) }
            }
        }
    }

    fun processImage(context: Context, bitmap: Bitmap) {
        viewModelScope.launch {
            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }
            val processor = CodeInputProcessor(context)
            when (val result = processor.processImage(bitmap)) {
                is InputResult.Success -> runAnalysis(result.files)
                is InputResult.Error -> _state.update { it.copy(phase = DebugPhase.Error(result.message)) }
            }
        }
    }

    fun processText(context: Context, code: String, language: SupportedLanguage? = null) {
        viewModelScope.launch {
            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }
            val processor = CodeInputProcessor(context)
            when (val result = processor.processText(code, language)) {
                is InputResult.Success -> runAnalysis(result.files)
                is InputResult.Error -> _state.update { it.copy(phase = DebugPhase.Error(result.message)) }
            }
        }
    }

    private suspend fun runAnalysis(files: List<CodeFile>) {
        _state.update { it.copy(phase = DebugPhase.AnalyzingCode, loadedFiles = files) }
        val analyses = mutableMapOf<String, AnalysisReport>()
        files.forEach { file -> analyses[file.fileName] = debugEngine.analyzeFile(file) }
        _state.update { it.copy(phase = DebugPhase.EvaluatingQuality, analysisReports = analyses) }
        val qualities = mutableMapOf<String, QualityReport>()
        files.forEach { file -> qualities[file.fileName] = qualityEvaluator.evaluateFile(file) }
        _state.update { it.copy(phase = DebugPhase.Complete, qualityReports = qualities) }
    }

    fun selectFile(index: Int) { _state.update { it.copy(selectedFileIndex = index) } }

    fun reset() { _state.update { CodeFinalizerState() } }

    fun updateChatInput(text: String) { _state.update { it.copy(chatInput = text) } }

    fun sendChatMessage(modelService: ModelService, userText: String) {
        if (userText.isBlank() || _state.value.isGeneratingChat || !modelService.isLLMLoaded) return
        _state.update {
            it.copy(
                chatMessages = it.chatMessages + DebugChatMessage(userText, isUser = true),
                chatInput = "",
                isGeneratingChat = true
            )
        }
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { RunAnywhere.chat(buildPrompt(userText)) }
                _state.update {
                    it.copy(
                        chatMessages = it.chatMessages + DebugChatMessage(response, isUser = false),
                        isGeneratingChat = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        chatMessages = it.chatMessages + DebugChatMessage("Error: ${e.message}", isUser = false),
                        isGeneratingChat = false
                    )
                }
            }
        }
    }

    fun askAboutErrors(modelService: ModelService) {
        val analysis = selectedAnalysis ?: return
        if (analysis.errors.isEmpty()) { sendChatMessage(modelService, "No errors found. Review my code for improvements."); return }
        val top = analysis.errors.take(3).joinToString("\n") { "Line ${it.lineNumber}: ${it.message}" }
        sendChatMessage(modelService, "Explain and fix:\n$top")
    }

    fun askAboutQuality(modelService: ModelService) {
        val q = selectedQuality ?: return
        val top = q.issues.take(3).joinToString("\n") { "Line ${it.lineNumber}: ${it.improvementHint}" }
        sendChatMessage(modelService, "Quality score: ${q.overallScore}/100. Top issues:\n$top\nHow to improve?")
    }

    fun askForFix(modelService: ModelService) {
        val file = selectedFile ?: return
        val analysis = selectedAnalysis ?: return
        val snippet = file.normalizedContent.lines().take(25).joinToString("\n")
        val errors = analysis.errors.take(3).joinToString("\n") { "Line ${it.lineNumber}: ${it.message}" }
        sendChatMessage(modelService, "Fix these ${file.language.displayName} errors:\n$errors\n\n```\n$snippet\n```")
    }

    private fun buildPrompt(userQuestion: String): String {
        val file = selectedFile ?: return userQuestion
        val analysis = selectedAnalysis
        val errorSummary = analysis?.errors?.take(5)?.joinToString("\n") { "Line ${it.lineNumber}: ${it.message}" } ?: "None"
        val snippet = file.normalizedContent.lines().take(20).joinToString("\n")
        return """You are a ${file.language.displayName} debugger. Be concise.
Code (${file.lineCount} lines):
```
$snippet
```
Errors: $errorSummary
Question: $userQuestion
Answer:"""
    }
}

