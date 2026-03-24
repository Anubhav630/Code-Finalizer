package com.runanywhere.kotlin_starter_example.debugger.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.kotlin_starter_example.debugger.analysis.AnalysisReport
import com.runanywhere.kotlin_starter_example.debugger.analysis.CoreDebugEngine
import com.runanywhere.kotlin_starter_example.debugger.analysis.ProjectAnalyzer
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
import com.runanywhere.kotlin_starter_example.debugger.llm.LLMSolutionGenerator
import com.runanywhere.kotlin_starter_example.debugger.services.VisionService

data class DebugChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val showImageSourceDialog: Boolean = false


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
    val chatInput: String = "",
    val aiSolutions: Map<String, String> = emptyMap(),
    val aiDiagnosis: String? = null,
    val extractedPreview: String? = null,
    val showImageSourceDialog: Boolean = false
)

class CodeFinalizerViewModel : ViewModel() {

    private val _state = MutableStateFlow(CodeFinalizerState())
    val state: StateFlow<CodeFinalizerState> = _state.asStateFlow()
    private val debugEngine = CoreDebugEngine()
    private val qualityEvaluator = QualityEvaluator()
    private val llmSolutionGenerator = LLMSolutionGenerator()
    private val llmGenerator = LLMSolutionGenerator()

    private var pendingImageFiles: List<CodeFile>? = null
    private var pendingModelService: ModelService? = null

    lateinit var visionService: VisionService

    fun openImageSourceDialog() {
        _state.update { it.copy(showImageSourceDialog = true) }
    }

    fun closeImageSourceDialog() {
        _state.update { it.copy(showImageSourceDialog = false) }
    }




    val selectedFile: CodeFile?
        get() = _state.value.loadedFiles.getOrNull(_state.value.selectedFileIndex)

    val selectedAnalysis: AnalysisReport?
        get() = selectedFile?.let { _state.value.analysisReports[it.fileName] }

    val selectedQuality: QualityReport?
        get() = selectedFile?.let { _state.value.qualityReports[it.fileName] }

    fun processZip(
        context: Context,
        uri: Uri,
        modelService: ModelService
    ) {
        viewModelScope.launch {
            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }
            val processor = CodeInputProcessor(context)
            when (val result = processor.processZip(uri)) {
                is InputResult.Success -> runAnalysis(result.files, modelService)
                is InputResult.Error -> _state.update { it.copy(phase = DebugPhase.Error(result.message)) }
            }
        }
    }

    fun attachContext(context: Context, modelService: ModelService) {
        visionService = VisionService(context, modelService)
    }

    fun processImage(
        context: Context,
        uri: Uri,
        modelService: ModelService
    ) {
        viewModelScope.launch {

            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }

            try {
                val bitmap =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(
                                context.contentResolver,
                                uri
                            )
                        )
                    } else {
                        MediaStore.Images.Media.getBitmap(
                            context.contentResolver,
                            uri
                        )
                    }

                val processor = CodeInputProcessor(context)

                when (val result = processor.processImage(bitmap)) {
                    is InputResult.Success -> runAnalysis(result.files, modelService)
                    is InputResult.Error -> _state.update {
                        it.copy(phase = DebugPhase.Error(result.message))
                    }
                }

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = DebugPhase.Error(
                            "Failed to read image: ${e.message}"
                        )
                    )
                }
            }
        }
    }
    fun processText(
        context: Context,
        code: String,
        modelService: ModelService,
        language: SupportedLanguage? = null
    ) {
        viewModelScope.launch {

            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }

            val processor = CodeInputProcessor(context)

            when (val result = processor.processText(code, language)) {
                is InputResult.Success -> runAnalysis(result.files, modelService)
                is InputResult.Error -> _state.update {
                    it.copy(phase = DebugPhase.Error(result.message))
                }
            }
        }
    }

    private suspend fun runAnalysis(
        files: List<CodeFile>,
        modelService: ModelService
    ){

        if (!modelService.isLLMLoaded) {
            modelService.downloadAndLoadLLM()
        }

        _state.update { it.copy(phase = DebugPhase.AnalyzingCode, loadedFiles = files) }

        val analyses = mutableMapOf<String, AnalysisReport>()

        files.forEach { file ->
            analyses[file.fileName] = debugEngine.analyzeFile(file)
        }

        _state.update {
            it.copy(
                phase = DebugPhase.EvaluatingQuality,
                analysisReports = analyses
            )
        }

        val qualities = mutableMapOf<String, QualityReport>()

        files.forEach { file ->
            qualities[file.fileName] = qualityEvaluator.evaluateFile(file)
        }

        // ⭐ Project intelligence (already correct)
        val projectInsights = ProjectAnalyzer.analyze(files)

        // ⭐ Generate AI diagnosis from worst file
        val worstFileName = projectInsights.worstFile
        val worstFile = files.find { it.fileName == worstFileName }

        var aiText: String? = null

        if (worstFile != null) {
            val report = analyses[worstFile.fileName]
            if (report != null) {
                val solution =
                    llmSolutionGenerator.generateDebugSolution(
                        worstFile,
                        report
                    )
                aiText = solution.generatedSolution
            }
        }

        android.util.Log.d("AI_DEBUGGER", "Worst File: ${projectInsights.worstFile}")
        android.util.Log.d("AI_DEBUGGER", "Health Score: ${projectInsights.healthScore}")
        android.util.Log.d("AI_DEBUGGER", "Summary: ${projectInsights.summary}")

        // ⭐ NEW — AUTO LLM DIAGNOSIS
        val aiMap = mutableMapOf<String, String>()

        files.forEach { file ->

            val report = analyses[file.fileName]
            if (report != null) {

                val solution = llmGenerator.generateDebugSolution(
                    file,
                    report
                )

                aiMap[file.fileName] = solution.generatedSolution
            }
        }

        _state.update {
            it.copy(
                phase = DebugPhase.Complete,
                qualityReports = qualities,
                aiDiagnosis = aiText   // ⭐ ADD THIS
            )
        }
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

    fun processImageBitmap(bitmap: Bitmap, modelService: ModelService) {

        _state.update { it.copy(phase = DebugPhase.AnalyzingCode) }

        viewModelScope.launch {

            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }

            val result = visionService.extractCodeFromImage(bitmap)
            if (!result.isCode || result.extractedCode == null) {

                _state.update {
                    it.copy(
                        phase = DebugPhase.Error(result.message)
                    )
                }
                return@launch
            }

            val raw = result.extractedCode ?: ""

            val normalized = raw.trim()

            val codeFile = CodeFile(
                fileName = "image_code.java",
                language = SupportedLanguage.JAVA,   // ⭐ temporary default
                rawContent = raw,
                normalizedContent = normalized,
                lineCount = normalized.lines().size
            )
            pendingImageFiles = listOf(codeFile)
            pendingModelService = modelService

            _state.update {
                it.copy(
                    extractedPreview = raw.take(800)
                )
            }
        }

    }

    fun processImageUri(
        context: Context,
        uri: Uri,
        modelService: ModelService
    ) {
        viewModelScope.launch {

            _state.update { it.copy(phase = DebugPhase.ProcessingInput) }

            try {
                val bitmap =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(
                                context.contentResolver,
                                uri
                            )
                        )
                    } else {
                        MediaStore.Images.Media.getBitmap(
                            context.contentResolver,
                            uri
                        )
                    }

                // ⭐ NOW CALL VLM PIPELINE
                processImageBitmap(bitmap, modelService)

            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        phase = DebugPhase.Error(
                            "Failed to read image: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun confirmImageCode() {
        val files = pendingImageFiles ?: return
        val model = pendingModelService ?: return

        viewModelScope.launch {
            runAnalysis(files, model)
        }

        pendingImageFiles = null
        pendingModelService = null

        _state.update { it.copy(extractedPreview = null) }
    }

    fun cancelImageCode() {
        pendingImageFiles = null
        pendingModelService = null
        _state.update { it.copy(extractedPreview = null) }
    }

}

