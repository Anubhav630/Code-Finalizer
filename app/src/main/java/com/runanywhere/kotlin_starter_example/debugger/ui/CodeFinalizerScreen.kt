@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.runanywhere.kotlin_starter_example.debugger.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.runanywhere.kotlin_starter_example.debugger.analysis.DebugError
import com.runanywhere.kotlin_starter_example.debugger.analysis.Severity
import com.runanywhere.kotlin_starter_example.debugger.llm.FullDebugSession
import com.runanywhere.kotlin_starter_example.debugger.quality.IssueCategory
import com.runanywhere.kotlin_starter_example.debugger.quality.IssueSeverity
import com.runanywhere.kotlin_starter_example.debugger.quality.QualityIssue

// ─────────────────────────────────────────────
//  Color Palette
// ─────────────────────────────────────────────

private val PrimaryBlue = Color(0xFF1E88E5)
private val SurfaceDark = Color(0xFF1A1A2E)
private val CardDark = Color(0xFF16213E)
private val AccentGreen = Color(0xFF00C853)
private val AccentRed = Color(0xFFFF5252)
private val AccentYellow = Color(0xFFFFD600)
private val AccentOrange = Color(0xFFFF6D00)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF9E9E9E)
private val CodeBackground = Color(0xFF0D1117)

// ─────────────────────────────────────────────
//  Main Screen
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeFinalizerScreen(
    viewModel: CodeFinalizerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // File picker launchers
    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.processZipFile(context, it) } }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            viewModel.processImage(context, bitmap)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = PrimaryBlue)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Code Finalizer",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
                actions = {
                    if (state.phase !is DebugPhase.Idle) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = TextSecondary)
                        }
                    }
                }
            )
        },
        containerColor = SurfaceDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(targetState = state.phase, label = "phase") { phase ->
                when (phase) {
                    is DebugPhase.Idle -> InputSelectionPanel(
                        onPickZip = { zipLauncher.launch("application/zip") },
                        onPickImage = { imageLauncher.launch("image/*") },
                        onPasteCode = { code -> viewModel.processTextInput(context, code) }
                    )
                    is DebugPhase.ProcessingInput,
                    is DebugPhase.AnalyzingCode,
                    is DebugPhase.EvaluatingQuality,
                    is DebugPhase.GeneratingSolution -> LoadingPanel(phase)
                    is DebugPhase.Complete -> ResultsPanel(
                        sessions = phase.sessions,
                        state = state,
                        viewModel = viewModel
                    )
                    is DebugPhase.Error -> ErrorPanel(phase.message) { viewModel.reset() }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Input Selection Panel
// ─────────────────────────────────────────────

@Composable
private fun InputSelectionPanel(
    onPickZip: () -> Unit,
    onPickImage: () -> Unit,
    onPasteCode: (String) -> Unit
) {
    var pastedCode by remember { mutableStateOf("") }
    var showPasteArea by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        // Hero
        Icon(
            Icons.Default.Code,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("Offline Code Debugger", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "3-phase analysis: Syntax → Semantic → Quality → AI Fix",
            color = TextSecondary, fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(32.dp))

        // Input options
        InputOptionCard(
            icon = Icons.Default.FolderZip,
            title = "Upload ZIP Project",
            subtitle = "Analyzes all code files in the archive",
            color = PrimaryBlue,
            onClick = onPickZip
        )
        Spacer(Modifier.height(12.dp))
        InputOptionCard(
            icon = Icons.Default.Image,
            title = "Upload Code Image",
            subtitle = "OCR extracts and analyzes code from photo",
            color = Color(0xFF7C4DFF),
            onClick = onPickImage
        )
        Spacer(Modifier.height(12.dp))
        InputOptionCard(
            icon = Icons.Default.ContentPaste,
            title = "Paste Code Directly",
            subtitle = "Type or paste your code for instant analysis",
            color = AccentGreen,
            onClick = { showPasteArea = !showPasteArea }
        )

        AnimatedVisibility(showPasteArea, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                OutlinedTextField(
                    value = pastedCode,
                    onValueChange = { pastedCode = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    label = { Text("Paste your code here", color = TextSecondary) },
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        fontSize = 13.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = TextSecondary,
                        focusedContainerColor = CodeBackground,
                        unfocusedContainerColor = CodeBackground
                    )
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { if (pastedCode.isNotBlank()) onPasteCode(pastedCode) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    enabled = pastedCode.isNotBlank()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Analyze Code")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Phase badges
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhaseBadge("1", "Input", Color(0xFF0288D1))
            Text("→", color = TextSecondary)
            PhaseBadge("2", "Debug", AccentRed)
            Text("→", color = TextSecondary)
            PhaseBadge("3", "Quality", AccentYellow)
            Text("→", color = TextSecondary)
            PhaseBadge("AI", "Fix", AccentGreen)
        }
    }
}

@Composable
private fun InputOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun PhaseBadge(number: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.2f))
                .border(1.dp, color, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}

// ─────────────────────────────────────────────
//  Loading Panel
// ─────────────────────────────────────────────

@Composable
private fun LoadingPanel(phase: DebugPhase) {
    val (title, subtitle) = when (phase) {
        is DebugPhase.ProcessingInput -> "📂 Processing Input" to "Extracting and normalizing code..."
        is DebugPhase.AnalyzingCode -> "🔍 Analyzing Code" to "Running syntax & semantic checks..."
        is DebugPhase.EvaluatingQuality -> "📊 Evaluating Quality" to "Checking PMD/Pylint/ESLint rules..."
        is DebugPhase.GeneratingSolution -> "🤖 Generating AI Fix" to "Running on-device SmolLM2..."
        else -> "Processing..." to ""
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(56.dp), strokeWidth = 4.dp)
            Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
        }
    }
}

// ─────────────────────────────────────────────
//  Results Panel
// ─────────────────────────────────────────────

@Composable
private fun ResultsPanel(
    sessions: List<FullDebugSession>,
    state: CodeFinalizerState,
    viewModel: CodeFinalizerViewModel
) {
    val session = state.currentSession ?: sessions.firstOrNull() ?: return
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {

        // File tabs (if multiple files)
        if (sessions.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = state.selectedFileIndex,
                containerColor = CardDark,
                contentColor = PrimaryBlue,
                edgePadding = 8.dp
            ) {
                sessions.forEachIndexed { idx, s ->
                    Tab(
                        selected = state.selectedFileIndex == idx,
                        onClick = { viewModel.selectFile(idx) },
                        text = {
                            Text(
                                s.codeFile.fileName.substringAfterLast('/').take(20),
                                color = if (state.selectedFileIndex == idx) PrimaryBlue else TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }
        }

        // Summary bar
        SummaryBar(session)

        // Tab navigation: Errors | Quality | AI Fix
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceDark,
            contentColor = PrimaryBlue
        ) {
            val tabs = listOf(
                "🔴 Errors (${session.analysisReport.errors.size})",
                "📊 Quality (${session.qualityReport.overallScore}/100)",
                "🤖 AI Fix"
            )
            tabs.forEachIndexed { idx, title ->
                Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }) {
                    Text(title, color = if (selectedTab == idx) PrimaryBlue else TextSecondary,
                        fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }

        when (selectedTab) {
            0 -> ErrorsTab(session)
            1 -> QualityTab(session)
            2 -> AiFixTab(session, state, viewModel)
        }
    }
}

@Composable
private fun SummaryBar(session: FullDebugSession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardDark)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                session.codeFile.fileName.substringAfterLast('/'),
                color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
            )
            Text(
                "${session.codeFile.language.displayName} · ${session.codeFile.lineCount} lines · ${session.sessionDurationMs}ms",
                color = TextSecondary, fontSize = 11.sp
            )
        }
        QualityGauge(session.qualityReport.overallScore)
    }
}

@Composable
private fun QualityGauge(score: Int) {
    val color = when {
        score >= 80 -> AccentGreen
        score >= 60 -> AccentYellow
        score >= 40 -> AccentOrange
        else -> AccentRed
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$score", color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp
        )
        Text("/100", color = TextSecondary, fontSize = 10.sp)
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = Color.White.copy(alpha = 0.1f)
        )
    }
}

// ─────────────────────────────────────────────
//  Errors Tab
// ─────────────────────────────────────────────

@Composable
private fun ErrorsTab(session: FullDebugSession) {
    if (session.analysisReport.errors.isEmpty()) {
        EmptyState("✅ No syntax or semantic errors found!", AccentGreen)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(session.analysisReport.errors) { error ->
            ErrorCard(error)
        }
    }
}

@Composable
private fun ErrorCard(error: DebugError) {
    val (bgColor, borderColor) = when (error.severity) {
        Severity.CRITICAL -> Color(0xFF3E1010) to AccentRed
        Severity.HIGH -> Color(0xFF3E2610) to AccentOrange
        Severity.MEDIUM -> Color(0xFF3E3510) to AccentYellow
        Severity.LOW -> Color(0xFF1A2A1A) to AccentGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    error.severity.name,
                    color = borderColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(borderColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Line ${error.lineNumber}", color = TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${(error.confidence * 100).toInt()}% confidence",
                    color = TextSecondary, fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(error.message, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                error.errorType.name.replace("_", " "),
                color = TextSecondary, fontSize = 11.sp
            )
            if (error.errorContext.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    error.errorContext.take(80),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFB0BEC5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodeBackground, RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (error.suggestion.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null,
                        tint = AccentYellow, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(error.suggestion, color = AccentYellow, fontSize = 11.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Quality Tab
// ─────────────────────────────────────────────

@Composable
private fun QualityTab(session: FullDebugSession) {
    val report = session.qualityReport
    val metrics = report.complexityMetrics

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Metrics overview
        item {
            MetricsCard(
                cyclomatic = metrics.cyclomaticComplexity,
                loc = metrics.linesOfCode,
                nesting = metrics.maxNestingDepth,
                avgFunc = metrics.avgFunctionLength,
                commentRatio = metrics.commentRatio
            )
        }

        // Issues by category
        val grouped = report.issues.groupBy { it.category }
        grouped.forEach { (category, issues) ->
            item {
                Text(
                    "  ${category.name.replace("_", " ")} (${issues.size})",
                    color = TextSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(issues) { issue ->
                QualityIssueCard(issue)
            }
        }

        if (report.issues.isEmpty()) {
            item { EmptyState("✅ Code quality looks great!", AccentGreen) }
        }
    }
}

@Composable
private fun MetricsCard(
    cyclomatic: Int, loc: Int, nesting: Int, avgFunc: Int, commentRatio: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Complexity Metrics", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("Cyclomatic", cyclomatic.toString(), if (cyclomatic > 10) AccentRed else AccentGreen)
                MetricItem("Lines", loc.toString(), TextPrimary)
                MetricItem("Max Nesting", nesting.toString(), if (nesting > 4) AccentYellow else AccentGreen)
                MetricItem("Avg Func", "${avgFunc}L", if (avgFunc > 50) AccentOrange else AccentGreen)
                MetricItem("Comments", "${(commentRatio * 100).toInt()}%",
                    if (commentRatio > 0.1f) AccentGreen else AccentYellow)
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun QualityIssueCard(issue: QualityIssue) {
    val severityColor = when (issue.severity) {
        IssueSeverity.BLOCKER -> AccentRed
        IssueSeverity.CRITICAL -> Color(0xFFFF6D00)
        IssueSeverity.MAJOR -> AccentYellow
        IssueSeverity.MINOR -> Color(0xFF40C4FF)
        IssueSeverity.INFO -> TextSecondary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(50.dp)
                    .background(severityColor, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        issue.severity.label,
                        color = severityColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(severityColor.copy(0.1f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(issue.ruleSource, color = TextSecondary, fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    Text("L${issue.lineNumber}", color = TextSecondary, fontSize = 10.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(issue.improvementHint, color = TextPrimary, fontSize = 12.sp)
                if (issue.lineContext.isNotBlank()) {
                    Text(
                        issue.lineContext.take(60),
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        color = Color(0xFF78909C),
                        modifier = Modifier.padding(top = 4.dp),
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  AI Fix Tab
// ─────────────────────────────────────────────

@Composable
private fun AiFixTab(
    session: FullDebugSession,
    state: CodeFinalizerState,
    viewModel: CodeFinalizerViewModel
) {
    var userQuestion by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // LLM solution if available
        val llm = session.llmSolution
        if (llm != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, contentDescription = null,
                            tint = AccentGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI Analysis", color = AccentGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(llm.modelUsed, color = TextSecondary, fontSize = 10.sp)
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(0.1f))
                    Text(llm.generatedSolution, color = TextPrimary, fontSize = 13.sp)

                    if (llm.codeSnippet != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Fixed Code:", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            llm.codeSnippet,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF80CBC4),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CodeBackground, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null,
                        tint = PrimaryBlue, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Code looks clean!", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("No AI fix needed — quality score is good.",
                        color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        // Streaming output
        if (state.isStreaming || state.streamingOutput.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isStreaming) {
                            CircularProgressIndicator(
                                color = PrimaryBlue,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (state.isStreaming) "Thinking..." else "Answer",
                            color = PrimaryBlue, fontWeight = FontWeight.Bold
                        )
                    }
                    if (state.userQuestion.isNotBlank()) {
                        Text("Q: ${state.userQuestion}", color = TextSecondary,
                            fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(0.1f))
                    Text(state.streamingOutput, color = TextPrimary, fontSize = 13.sp)
                }
            }
        }

        // Ask a question
        Spacer(Modifier.height(16.dp))
        Text("Ask the AI", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))

        // Quick question chips
        val quickQuestions = listOf(
            "What is the worst bug?",
            "How can I optimize this?",
            "Explain the logic",
            "How to add error handling?"
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickQuestions.forEach { q ->
                FilterChip(
                    selected = false,
                    onClick = { viewModel.askQuestion(q) },
                    label = { Text(q, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CardDark,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = userQuestion,
            onValueChange = { userQuestion = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask anything about this code...", color = TextSecondary) },
            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = TextSecondary.copy(0.5f),
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark
            ),
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (userQuestion.isNotBlank()) {
                            viewModel.askQuestion(userQuestion)
                            userQuestion = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = PrimaryBlue)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────
//  Utility Composables
// ─────────────────────────────────────────────

@Composable
private fun EmptyState(message: String, color: Color) {
    Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(message, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRed, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Something went wrong", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(message, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Try Again")
            }
        }
    }
}
