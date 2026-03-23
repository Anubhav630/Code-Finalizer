package com.runanywhere.kotlin_starter_example.debugger.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.kotlin_starter_example.debugger.analysis.AnalysisReport
import com.runanywhere.kotlin_starter_example.debugger.analysis.DebugError
import com.runanywhere.kotlin_starter_example.debugger.input.CodeFile
import com.runanywhere.kotlin_starter_example.debugger.input.CodeInputProcessor
import com.runanywhere.kotlin_starter_example.debugger.input.InputResult
import com.runanywhere.kotlin_starter_example.debugger.input.SupportedLanguage
import com.runanywhere.kotlin_starter_example.debugger.llm.CodeFinalizerPipeline
import com.runanywhere.kotlin_starter_example.debugger.llm.FullDebugSession
import com.runanywhere.kotlin_starter_example.debugger.llm.LLMSolutionGenerator
import com.runanywhere.kotlin_starter_example.debugger.quality.QualityReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────

sealed class DebugPhase {
    object Idle : DebugPhase()
    object ProcessingInput : DebugPhase()     // Phase 1
    object AnalyzingCode : DebugPhase()       // Phase 2
    object EvaluatingQuality : DebugPhase()   // Phase 3
    object GeneratingSolution : DebugPhase()  // LLM
    data class Complete(val sessions: List<FullDebugSession>) : DebugPhase()
    data class Error(val message: String) : DebugPhase()
}

data class CodeFinalizerState(
    val phase: DebugPhase = DebugPhase.Idle,
    val loadedFiles: List<CodeFile> = emptyList(),
    val selectedFileIndex: Int = 0,
    val currentSession: FullDebugSession? = null,
    val allSessions: List<FullDebugSession> = emptyList(),
    val streamingOutput: String = "",
    val isStreaming: Boolean = false,
    val userQuestion: String = ""
)

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

class CodeFinalizerViewModel : ViewModel() {

    private val _state = MutableStateFlow(CodeFinalizerState())
    val state: StateFlow<CodeFinalizerState> = _state.asStateFlow()

    private val pipeline = CodeFinalizerPipeline()
    private val llmGenerator = LLMSolutionGenerator()

    // ── Input Handlers ────────────────────────────────────────────────────

    fun processZipFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }
            val processor = CodeInputProcessor(context)
            when (val result = processor.processZip(uri)) {
                is InputResult.Success -> onFilesLoaded(result.files)
                is InputResult.Error -> _state.update {
                    it.copy(phase = DebugPhase.Error(result.message))
                }
            }
        }
    }

    fun processImage(context: Context, bitmap: Bitmap) {
        viewModelScope.launch {
            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }
            val processor = CodeInputProcessor(context)
            when (val result = processor.processImage(bitmap)) {
                is InputResult.Success -> onFilesLoaded(result.files)
                is InputResult.Error -> _state.update {
                    it.copy(phase = DebugPhase.Error(result.message))
                }
            }
        }
    }

    fun processTextInput(context: Context, code: String, language: SupportedLanguage? = null) {
        viewModelScope.launch {
            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }
            val processor = CodeInputProcessor(context)
            when (val result = processor.processText(code, language)) {
                is InputResult.Success -> onFilesLoaded(result.files)
                is InputResult.Error -> _state.update {
                    it.copy(phase = DebugPhase.Error(result.message))
                }
            }
        }
    }

    // ── Pipeline Execution ───────────────────────────────────────────────

    private suspend fun onFilesLoaded(files: List<CodeFile>) {
        _state.update { it.copy(loadedFiles = files) }

        // Phase 2 — Analysis
        _state.update { it.copy(phase = DebugPhase.AnalyzingCode) }

        // Phase 3 — Quality
        _state.update { it.copy(phase = DebugPhase.EvaluatingQuality) }

        // Run full pipeline on all files
        _state.update { it.copy(phase = DebugPhase.GeneratingSolution) }
        val sessions = pipeline.runOnProject(files)

        // Run LLM on the worst file (lowest quality score)
        val worstSession = sessions.minByOrNull { it.qualityReport.overallScore }
        val finalSessions = if (worstSession != null) {
            val fullSession = pipeline.runFullPipeline(worstSession.codeFile, generateLLMSolution = true)
            sessions.map { if (it.codeFile.fileName == fullSession.codeFile.fileName) fullSession else it }
        } else sessions

        _state.update {
            it.copy(
                phase = DebugPhase.Complete(finalSessions),
                allSessions = finalSessions,
                currentSession = finalSessions.firstOrNull()
            )
        }
    }

    fun runPipelineOnFile(index: Int) {
        val file = _state.value.loadedFiles.getOrNull(index) ?: return
        viewModelScope.launch {
            _state.update { it.copy(phase = DebugPhase.AnalyzingCode, selectedFileIndex = index) }
            val session = pipeline.runFullPipeline(file, generateLLMSolution = true)
            _state.update { state ->
                val updated = state.allSessions.toMutableList()
                val existingIdx = updated.indexOfFirst { it.codeFile.fileName == file.fileName }
                if (existingIdx >= 0) updated[existingIdx] = session else updated.add(session)
                state.copy(
                    phase = DebugPhase.Complete(updated),
                    allSessions = updated,
                    currentSession = session
                )
            }
        }
    }

    fun askQuestion(question: String) {
        val file = _state.value.currentSession?.codeFile ?: return
        viewModelScope.launch {
            _state.update { it.copy(isStreaming = true, streamingOutput = "", userQuestion = question) }
            llmGenerator.streamDebugSolution(
                file,
                _state.value.currentSession!!.analysisReport
            ).collect { chunk ->
                _state.update { it.copy(streamingOutput = it.streamingOutput + chunk) }
            }
            _state.update { it.copy(isStreaming = false) }
        }
    }

    fun selectFile(index: Int) {
        val session = _state.value.allSessions.getOrNull(index)
        _state.update { it.copy(selectedFileIndex = index, currentSession = session) }
    }

    fun reset() {
        _state.update { CodeFinalizerState() }
    }
}
