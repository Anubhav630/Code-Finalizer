//@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
//
//package com.runanywhere.kotlin_starter_example.debugger.ui
//
//import android.net.Uri
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.animation.AnimatedContent
//import androidx.compose.animation.AnimatedVisibility
//import androidx.compose.animation.core.animateFloatAsState
//import androidx.compose.animation.expandVertically
//import androidx.compose.animation.shrinkVertically
//import androidx.compose.foundation.background
//import androidx.compose.foundation.border
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.foundation.rememberScrollState
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.foundation.verticalScroll
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.font.FontFamily
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.lifecycle.viewmodel.compose.viewModel
//import androidx.compose.foundation.layout.ExperimentalLayoutApi
//import androidx.compose.foundation.layout.FlowRow
//import com.runanywhere.kotlin_starter_example.debugger.analysis.DebugError
//import com.runanywhere.kotlin_starter_example.debugger.analysis.Severity
//import com.runanywhere.kotlin_starter_example.debugger.llm.FullDebugSession
//import com.runanywhere.kotlin_starter_example.debugger.quality.IssueCategory
//import com.runanywhere.kotlin_starter_example.debugger.quality.IssueSeverity
//import com.runanywhere.kotlin_starter_example.debugger.quality.QualityIssue
//
//// ─────────────────────────────────────────────
////  Color Palette
//// ─────────────────────────────────────────────
//
//private val PrimaryBlue = Color(0xFF1E88E5)
//private val SurfaceDark = Color(0xFF1A1A2E)
//private val CardDark = Color(0xFF16213E)
//private val AccentGreen = Color(0xFF00C853)
//private val AccentRed = Color(0xFFFF5252)
//private val AccentYellow = Color(0xFFFFD600)
//private val AccentOrange = Color(0xFFFF6D00)
//private val TextPrimary = Color(0xFFE0E0E0)
//private val TextSecondary = Color(0xFF9E9E9E)
//private val CodeBackground = Color(0xFF0D1117)
//
//// ─────────────────────────────────────────────
////  Main Screen
//// ─────────────────────────────────────────────
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun CodeFinalizerScreen(
//    viewModel: CodeFinalizerViewModel = viewModel()
//) {
//    val state by viewModel.state.collectAsState()
//    val context = LocalContext.current
//
//    // File picker launchers
//    val zipLauncher = rememberLauncherForActivityResult(
//        ActivityResultContracts.GetContent()
//    ) { uri: Uri? -> uri?.let { viewModel.processZipFile(context, it) } }
//
//    val imageLauncher = rememberLauncherForActivityResult(
//        ActivityResultContracts.GetContent()
//    ) { uri: Uri? ->
//        uri?.let {
//            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
//            viewModel.processImage(context, bitmap)
//        }
//    }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = {
//                    Row(verticalAlignment = Alignment.CenterVertically) {
//                        Icon(Icons.Default.BugReport, contentDescription = null, tint = PrimaryBlue)
//                        Spacer(Modifier.width(8.dp))
//                        Text(
//                            "Code Finalizer",
//                            color = TextPrimary,
//                            fontWeight = FontWeight.Bold,
//                            fontSize = 20.sp
//                        )
//                    }
//                },
//                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
//                actions = {
//                    if (state.phase !is DebugPhase.Idle) {
//                        IconButton(onClick = { viewModel.reset() }) {
//                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = TextSecondary)
//                        }
//                    }
//                }
//            )
//        },
//        containerColor = SurfaceDark
//    ) { paddingValues ->
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(paddingValues)
//        ) {
//            AnimatedContent(targetState = state.phase, label = "phase") { phase ->
//                when (phase) {
//                    is DebugPhase.Idle -> InputSelectionPanel(
//                        onPickZip = { zipLauncher.launch("application/zip") },
//                        onPickImage = { imageLauncher.launch("image/*") },
//                        onPasteCode = { code -> viewModel.processTextInput(context, code) }
//                    )
//                    is DebugPhase.ProcessingInput,
//                    is DebugPhase.AnalyzingCode,
//                    is DebugPhase.EvaluatingQuality,
//                    is DebugPhase.GeneratingSolution -> LoadingPanel(phase)
//                    is DebugPhase.Complete -> ResultsPanel(
//                        sessions = phase.sessions,
//                        state = state,
//                        viewModel = viewModel
//                    )
//                    is DebugPhase.Error -> ErrorPanel(phase.message) { viewModel.reset() }
//                }
//            }
//        }
//    }
//}
//
//// ─────────────────────────────────────────────
////  Input Selection Panel
//// ─────────────────────────────────────────────
//
//@Composable
//private fun InputSelectionPanel(
//    onPickZip: () -> Unit,
//    onPickImage: () -> Unit,
//    onPasteCode: (String) -> Unit
//) {
//    var pastedCode by remember { mutableStateOf("") }
//    var showPasteArea by remember { mutableStateOf(false) }
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .verticalScroll(rememberScrollState())
//            .padding(20.dp),
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Spacer(Modifier.height(20.dp))
//
//        // Hero
//        Icon(
//            Icons.Default.Code,
//            contentDescription = null,
//            tint = PrimaryBlue,
//            modifier = Modifier.size(64.dp)
//        )
//        Spacer(Modifier.height(12.dp))
//        Text("Offline Code Debugger", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
//        Text(
//            "3-phase analysis: Syntax → Semantic → Quality → AI Fix",
//            color = TextSecondary, fontSize = 13.sp,
//            modifier = Modifier.padding(top = 4.dp)
//        )
//
//        Spacer(Modifier.height(32.dp))
//
//        // Input options
//        InputOptionCard(
//            icon = Icons.Default.FolderZip,
//            title = "Upload ZIP Project",
//            subtitle = "Analyzes all code files in the archive",
//            color = PrimaryBlue,
//            onClick = onPickZip
//        )
//        Spacer(Modifier.height(12.dp))
//        InputOptionCard(
//            icon = Icons.Default.Image,
//            title = "Upload Code Image",
//            subtitle = "OCR extracts and analyzes code from photo",
//            color = Color(0xFF7C4DFF),
//            onClick = onPickImage
//        )
//        Spacer(Modifier.height(12.dp))
//        InputOptionCard(
//            icon = Icons.Default.ContentPaste,
//            title = "Paste Code Directly",
//            subtitle = "Type or paste your code for instant analysis",
//            color = AccentGreen,
//            onClick = { showPasteArea = !showPasteArea }
//        )
//
//        AnimatedVisibility(showPasteArea, enter = expandVertically(), exit = shrinkVertically()) {
//            Column(modifier = Modifier.padding(top = 12.dp)) {
//                OutlinedTextField(
//                    value = pastedCode,
//                    onValueChange = { pastedCode = it },
//                    modifier = Modifier.fillMaxWidth().height(200.dp),
//                    label = { Text("Paste your code here", color = TextSecondary) },
//                    textStyle = LocalTextStyle.current.copy(
//                        fontFamily = FontFamily.Monospace,
//                        color = TextPrimary,
//                        fontSize = 13.sp
//                    ),
//                    colors = OutlinedTextFieldDefaults.colors(
//                        focusedBorderColor = PrimaryBlue,
//                        unfocusedBorderColor = TextSecondary,
//                        focusedContainerColor = CodeBackground,
//                        unfocusedContainerColor = CodeBackground
//                    )
//                )
//                Spacer(Modifier.height(8.dp))
//                Button(
//                    onClick = { if (pastedCode.isNotBlank()) onPasteCode(pastedCode) },
//                    modifier = Modifier.fillMaxWidth(),
//                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
//                    enabled = pastedCode.isNotBlank()
//                ) {
//                    Icon(Icons.Default.PlayArrow, contentDescription = null)
//                    Spacer(Modifier.width(8.dp))
//                    Text("Analyze Code")
//                }
//            }
//        }
//
//        Spacer(Modifier.height(24.dp))
//
//        // Phase badges
//        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//            PhaseBadge("1", "Input", Color(0xFF0288D1))
//            Text("→", color = TextSecondary)
//            PhaseBadge("2", "Debug", AccentRed)
//            Text("→", color = TextSecondary)
//            PhaseBadge("3", "Quality", AccentYellow)
//            Text("→", color = TextSecondary)
//            PhaseBadge("AI", "Fix", AccentGreen)
//        }
//    }
//}
//
//@Composable
//private fun InputOptionCard(
//    icon: androidx.compose.ui.graphics.vector.ImageVector,
//    title: String,
//    subtitle: String,
//    color: Color,
//    onClick: () -> Unit
//) {
//    Card(
//        modifier = Modifier.fillMaxWidth().clickable { onClick() },
//        shape = RoundedCornerShape(12.dp),
//        colors = CardDefaults.cardColors(containerColor = CardDark),
//        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
//    ) {
//        Row(
//            modifier = Modifier.padding(16.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Box(
//                modifier = Modifier
//                    .size(48.dp)
//                    .clip(RoundedCornerShape(10.dp))
//                    .background(color.copy(alpha = 0.15f)),
//                contentAlignment = Alignment.Center
//            ) {
//                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
//            }
//            Spacer(Modifier.width(16.dp))
//            Column {
//                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
//                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
//            }
//            Spacer(Modifier.weight(1f))
//            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
//        }
//    }
//}
//
//@Composable
//private fun PhaseBadge(number: String, label: String, color: Color) {
//    Column(horizontalAlignment = Alignment.CenterHorizontally) {
//        Box(
//            modifier = Modifier
//                .size(32.dp)
//                .clip(RoundedCornerShape(8.dp))
//                .background(color.copy(alpha = 0.2f))
//                .border(1.dp, color, RoundedCornerShape(8.dp)),
//            contentAlignment = Alignment.Center
//        ) {
//            Text(number, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
//        }
//        Text(label, color = TextSecondary, fontSize = 10.sp)
//    }
//}
//
//// ─────────────────────────────────────────────
////  Loading Panel
//// ─────────────────────────────────────────────
//
//@Composable
//private fun LoadingPanel(phase: DebugPhase) {
//    val (title, subtitle) = when (phase) {
//        is DebugPhase.ProcessingInput -> "📂 Processing Input" to "Extracting and normalizing code..."
//        is DebugPhase.AnalyzingCode -> "🔍 Analyzing Code" to "Running syntax & semantic checks..."
//        is DebugPhase.EvaluatingQuality -> "📊 Evaluating Quality" to "Checking PMD/Pylint/ESLint rules..."
//        is DebugPhase.GeneratingSolution -> "🤖 Generating AI Fix" to "Running on-device SmolLM2..."
//        else -> "Processing..." to ""
//    }
//    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
//            CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(56.dp), strokeWidth = 4.dp)
//            Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
//            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
//        }
//    }
//}
//
//// ─────────────────────────────────────────────
////  Results Panel
//// ─────────────────────────────────────────────
//
//@Composable
//private fun ResultsPanel(
//    sessions: List<FullDebugSession>,
//    state: CodeFinalizerState,
//    viewModel: CodeFinalizerViewModel
//) {
//    val session = state.currentSession ?: sessions.firstOrNull() ?: return
//    var selectedTab by remember { mutableStateOf(0) }
//
//    Column(modifier = Modifier.fillMaxSize()) {
//
//        // File tabs (if multiple files)
//        if (sessions.size > 1) {
//            ScrollableTabRow(
//                selectedTabIndex = state.selectedFileIndex,
//                containerColor = CardDark,
//                contentColor = PrimaryBlue,
//                edgePadding = 8.dp
//            ) {
//                sessions.forEachIndexed { idx, s ->
//                    Tab(
//                        selected = state.selectedFileIndex == idx,
//                        onClick = { viewModel.selectFile(idx) },
//                        text = {
//                            Text(
//                                s.codeFile.fileName.substringAfterLast('/').take(20),
//                                color = if (state.selectedFileIndex == idx) PrimaryBlue else TextSecondary,
//                                fontSize = 12.sp
//                            )
//                        }
//                    )
//                }
//            }
//        }
//
//        // Summary bar
//        SummaryBar(session)
//
//        // Tab navigation: Errors | Quality | AI Fix
//        TabRow(
//            selectedTabIndex = selectedTab,
//            containerColor = SurfaceDark,
//            contentColor = PrimaryBlue
//        ) {
//            val tabs = listOf(
//                "🔴 Errors (${session.analysisReport.errors.size})",
//                "📊 Quality (${session.qualityReport.overallScore}/100)",
//                "🤖 AI Fix"
//            )
//            tabs.forEachIndexed { idx, title ->
//                Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }) {
//                    Text(title, color = if (selectedTab == idx) PrimaryBlue else TextSecondary,
//                        fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
//                }
//            }
//        }
//
//        when (selectedTab) {
//            0 -> ErrorsTab(session)
//            1 -> QualityTab(session)
//            2 -> AiFixTab(session, state, viewModel)
//        }
//    }
//}
//
//@Composable
//private fun SummaryBar(session: FullDebugSession) {
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .background(CardDark)
//            .padding(horizontal = 16.dp, vertical = 10.dp),
//        verticalAlignment = Alignment.CenterVertically,
//        horizontalArrangement = Arrangement.SpaceBetween
//    ) {
//        Column {
//            Text(
//                session.codeFile.fileName.substringAfterLast('/'),
//                color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
//            )
//            Text(
//                "${session.codeFile.language.displayName} · ${session.codeFile.lineCount} lines · ${session.sessionDurationMs}ms",
//                color = TextSecondary, fontSize = 11.sp
//            )
//        }
//        QualityGauge(session.qualityReport.overallScore)
//    }
//}
//
//@Composable
//private fun QualityGauge(score: Int) {
//    val color = when {
//        score >= 80 -> AccentGreen
//        score >= 60 -> AccentYellow
//        score >= 40 -> AccentOrange
//        else -> AccentRed
//    }
//    Column(horizontalAlignment = Alignment.CenterHorizontally) {
//        Text(
//            "$score", color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp
//        )
//        Text("/100", color = TextSecondary, fontSize = 10.sp)
//        LinearProgressIndicator(
//            progress = { score / 100f },
//            modifier = Modifier.width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
//            color = color,
//            trackColor = Color.White.copy(alpha = 0.1f)
//        )
//    }
//}
//
//// ─────────────────────────────────────────────
////  Errors Tab
//// ─────────────────────────────────────────────
//
//@Composable
//private fun ErrorsTab(session: FullDebugSession) {
//    if (session.analysisReport.errors.isEmpty()) {
//        EmptyState("✅ No syntax or semantic errors found!", AccentGreen)
//        return
//    }
//    LazyColumn(
//        modifier = Modifier.fillMaxSize(),
//        contentPadding = PaddingValues(12.dp),
//        verticalArrangement = Arrangement.spacedBy(8.dp)
//    ) {
//        items(session.analysisReport.errors) { error ->
//            ErrorCard(error)
//        }
//    }
//}
//
//@Composable
//private fun ErrorCard(error: DebugError) {
//    val (bgColor, borderColor) = when (error.severity) {
//        Severity.CRITICAL -> Color(0xFF3E1010) to AccentRed
//        Severity.HIGH -> Color(0xFF3E2610) to AccentOrange
//        Severity.MEDIUM -> Color(0xFF3E3510) to AccentYellow
//        Severity.LOW -> Color(0xFF1A2A1A) to AccentGreen
//    }
//
//    Card(
//        modifier = Modifier.fillMaxWidth(),
//        colors = CardDefaults.cardColors(containerColor = bgColor),
//        shape = RoundedCornerShape(10.dp),
//        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.5f))
//    ) {
//        Column(modifier = Modifier.padding(14.dp)) {
//            Row(verticalAlignment = Alignment.CenterVertically) {
//                Text(
//                    error.severity.name,
//                    color = borderColor,
//                    fontSize = 10.sp,
//                    fontWeight = FontWeight.Bold,
//                    modifier = Modifier
//                        .background(borderColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
//                        .padding(horizontal = 6.dp, vertical = 2.dp)
//                )
//                Spacer(Modifier.width(8.dp))
//                Text("Line ${error.lineNumber}", color = TextSecondary, fontSize = 11.sp)
//                Spacer(Modifier.weight(1f))
//                Text(
//                    "${(error.confidence * 100).toInt()}% confidence",
//                    color = TextSecondary, fontSize = 10.sp
//                )
//            }
//            Spacer(Modifier.height(6.dp))
//            Text(error.message, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
//            Spacer(Modifier.height(4.dp))
//            Text(
//                error.errorType.name.replace("_", " "),
//                color = TextSecondary, fontSize = 11.sp
//            )
//            if (error.errorContext.isNotBlank()) {
//                Spacer(Modifier.height(6.dp))
//                Text(
//                    error.errorContext.take(80),
//                    fontFamily = FontFamily.Monospace,
//                    fontSize = 11.sp,
//                    color = Color(0xFFB0BEC5),
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .background(CodeBackground, RoundedCornerShape(4.dp))
//                        .padding(8.dp),
//                    overflow = TextOverflow.Ellipsis
//                )
//            }
//            if (error.suggestion.isNotBlank()) {
//                Spacer(Modifier.height(6.dp))
//                Row(verticalAlignment = Alignment.Top) {
//                    Icon(Icons.Default.Lightbulb, contentDescription = null,
//                        tint = AccentYellow, modifier = Modifier.size(14.dp).padding(top = 2.dp))
//                    Spacer(Modifier.width(4.dp))
//                    Text(error.suggestion, color = AccentYellow, fontSize = 11.sp)
//                }
//            }
//        }
//    }
//}
//
//// ─────────────────────────────────────────────
////  Quality Tab
//// ─────────────────────────────────────────────
//
//@Composable
//private fun QualityTab(session: FullDebugSession) {
//    val report = session.qualityReport
//    val metrics = report.complexityMetrics
//
//    LazyColumn(
//        modifier = Modifier.fillMaxSize(),
//        contentPadding = PaddingValues(12.dp),
//        verticalArrangement = Arrangement.spacedBy(8.dp)
//    ) {
//        // Metrics overview
//        item {
//            MetricsCard(
//                cyclomatic = metrics.cyclomaticComplexity,
//                loc = metrics.linesOfCode,
//                nesting = metrics.maxNestingDepth,
//                avgFunc = metrics.avgFunctionLength,
//                commentRatio = metrics.commentRatio
//            )
//        }
//
//        // Issues by category
//        val grouped = report.issues.groupBy { it.category }
//        grouped.forEach { (category, issues) ->
//            item {
//                Text(
//                    "  ${category.name.replace("_", " ")} (${issues.size})",
//                    color = TextSecondary, fontSize = 12.sp,
//                    modifier = Modifier.padding(vertical = 4.dp)
//                )
//            }
//            items(issues) { issue ->
//                QualityIssueCard(issue)
//            }
//        }
//
//        if (report.issues.isEmpty()) {
//            item { EmptyState("✅ Code quality looks great!", AccentGreen) }
//        }
//    }
//}
//
//@Composable
//private fun MetricsCard(
//    cyclomatic: Int, loc: Int, nesting: Int, avgFunc: Int, commentRatio: Float
//) {
//    Card(
//        modifier = Modifier.fillMaxWidth(),
//        colors = CardDefaults.cardColors(containerColor = CardDark),
//        shape = RoundedCornerShape(12.dp)
//    ) {
//        Column(modifier = Modifier.padding(16.dp)) {
//            Text("Complexity Metrics", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
//            Spacer(Modifier.height(12.dp))
//            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
//                MetricItem("Cyclomatic", cyclomatic.toString(), if (cyclomatic > 10) AccentRed else AccentGreen)
//                MetricItem("Lines", loc.toString(), TextPrimary)
//                MetricItem("Max Nesting", nesting.toString(), if (nesting > 4) AccentYellow else AccentGreen)
//                MetricItem("Avg Func", "${avgFunc}L", if (avgFunc > 50) AccentOrange else AccentGreen)
//                MetricItem("Comments", "${(commentRatio * 100).toInt()}%",
//                    if (commentRatio > 0.1f) AccentGreen else AccentYellow)
//            }
//        }
//    }
//}
//
//@Composable
//private fun MetricItem(label: String, value: String, color: Color) {
//    Column(horizontalAlignment = Alignment.CenterHorizontally) {
//        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
//        Text(label, color = TextSecondary, fontSize = 10.sp)
//    }
//}
//
//@Composable
//private fun QualityIssueCard(issue: QualityIssue) {
//    val severityColor = when (issue.severity) {
//        IssueSeverity.BLOCKER -> AccentRed
//        IssueSeverity.CRITICAL -> Color(0xFFFF6D00)
//        IssueSeverity.MAJOR -> AccentYellow
//        IssueSeverity.MINOR -> Color(0xFF40C4FF)
//        IssueSeverity.INFO -> TextSecondary
//    }
//    Card(
//        modifier = Modifier.fillMaxWidth(),
//        colors = CardDefaults.cardColors(containerColor = CardDark),
//        shape = RoundedCornerShape(8.dp)
//    ) {
//        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
//            Box(
//                modifier = Modifier
//                    .width(3.dp)
//                    .height(50.dp)
//                    .background(severityColor, RoundedCornerShape(2.dp))
//            )
//            Spacer(Modifier.width(10.dp))
//            Column(modifier = Modifier.weight(1f)) {
//                Row(verticalAlignment = Alignment.CenterVertically) {
//                    Text(
//                        issue.severity.label,
//                        color = severityColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
//                        modifier = Modifier.background(severityColor.copy(0.1f), RoundedCornerShape(3.dp))
//                            .padding(horizontal = 5.dp, vertical = 1.dp)
//                    )
//                    Spacer(Modifier.width(6.dp))
//                    Text(issue.ruleSource, color = TextSecondary, fontSize = 10.sp)
//                    Spacer(Modifier.weight(1f))
//                    Text("L${issue.lineNumber}", color = TextSecondary, fontSize = 10.sp)
//                }
//                Spacer(Modifier.height(4.dp))
//                Text(issue.improvementHint, color = TextPrimary, fontSize = 12.sp)
//                if (issue.lineContext.isNotBlank()) {
//                    Text(
//                        issue.lineContext.take(60),
//                        fontFamily = FontFamily.Monospace, fontSize = 10.sp,
//                        color = Color(0xFF78909C),
//                        modifier = Modifier.padding(top = 4.dp),
//                        overflow = TextOverflow.Ellipsis
//                    )
//                }
//            }
//        }
//    }
//}
//
//// ─────────────────────────────────────────────
////  AI Fix Tab
//// ─────────────────────────────────────────────
//
//@Composable
//private fun AiFixTab(
//    session: FullDebugSession,
//    state: CodeFinalizerState,
//    viewModel: CodeFinalizerViewModel
//) {
//    var userQuestion by remember { mutableStateOf("") }
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .verticalScroll(rememberScrollState())
//            .padding(12.dp)
//    ) {
//        // LLM solution if available
//        val llm = session.llmSolution
//        if (llm != null) {
//            Card(
//                modifier = Modifier.fillMaxWidth(),
//                colors = CardDefaults.cardColors(containerColor = CardDark),
//                shape = RoundedCornerShape(12.dp),
//                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(0.3f))
//            ) {
//                Column(modifier = Modifier.padding(14.dp)) {
//                    Row(verticalAlignment = Alignment.CenterVertically) {
//                        Icon(Icons.Default.SmartToy, contentDescription = null,
//                            tint = AccentGreen, modifier = Modifier.size(18.dp))
//                        Spacer(Modifier.width(8.dp))
//                        Text("AI Analysis", color = AccentGreen, fontWeight = FontWeight.Bold)
//                        Spacer(Modifier.weight(1f))
//                        Text(llm.modelUsed, color = TextSecondary, fontSize = 10.sp)
//                    }
//                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(0.1f))
//                    Text(llm.generatedSolution, color = TextPrimary, fontSize = 13.sp)
//
//                    if (llm.codeSnippet != null) {
//                        Spacer(Modifier.height(8.dp))
//                        Text("Fixed Code:", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
//                        Spacer(Modifier.height(4.dp))
//                        Text(
//                            llm.codeSnippet,
//                            fontFamily = FontFamily.Monospace,
//                            fontSize = 11.sp,
//                            color = Color(0xFF80CBC4),
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .background(CodeBackground, RoundedCornerShape(8.dp))
//                                .padding(12.dp)
//                        )
//                    }
//                }
//            }
//        } else {
//            Card(
//                modifier = Modifier.fillMaxWidth(),
//                colors = CardDefaults.cardColors(containerColor = CardDark),
//                shape = RoundedCornerShape(12.dp)
//            ) {
//                Column(
//                    modifier = Modifier.padding(20.dp),
//                    horizontalAlignment = Alignment.CenterHorizontally
//                ) {
//                    Icon(Icons.Default.SmartToy, contentDescription = null,
//                        tint = PrimaryBlue, modifier = Modifier.size(40.dp))
//                    Spacer(Modifier.height(8.dp))
//                    Text("Code looks clean!", color = TextPrimary, fontWeight = FontWeight.SemiBold)
//                    Text("No AI fix needed — quality score is good.",
//                        color = TextSecondary, fontSize = 12.sp)
//                }
//            }
//        }
//
//        // Streaming output
//        if (state.isStreaming || state.streamingOutput.isNotEmpty()) {
//            Spacer(Modifier.height(16.dp))
//            Card(
//                modifier = Modifier.fillMaxWidth(),
//                colors = CardDefaults.cardColors(containerColor = CardDark),
//                shape = RoundedCornerShape(12.dp)
//            ) {
//                Column(modifier = Modifier.padding(14.dp)) {
//                    Row(verticalAlignment = Alignment.CenterVertically) {
//                        if (state.isStreaming) {
//                            CircularProgressIndicator(
//                                color = PrimaryBlue,
//                                modifier = Modifier.size(14.dp),
//                                strokeWidth = 2.dp
//                            )
//                            Spacer(Modifier.width(8.dp))
//                        }
//                        Text(
//                            if (state.isStreaming) "Thinking..." else "Answer",
//                            color = PrimaryBlue, fontWeight = FontWeight.Bold
//                        )
//                    }
//                    if (state.userQuestion.isNotBlank()) {
//                        Text("Q: ${state.userQuestion}", color = TextSecondary,
//                            fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
//                    }
//                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(0.1f))
//                    Text(state.streamingOutput, color = TextPrimary, fontSize = 13.sp)
//                }
//            }
//        }
//
//        // Ask a question
//        Spacer(Modifier.height(16.dp))
//        Text("Ask the AI", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
//        Spacer(Modifier.height(8.dp))
//
//        // Quick question chips
//        val quickQuestions = listOf(
//            "What is the worst bug?",
//            "How can I optimize this?",
//            "Explain the logic",
//            "How to add error handling?"
//        )
//        FlowRow(
//            horizontalArrangement = Arrangement.spacedBy(8.dp),
//            verticalArrangement = Arrangement.spacedBy(8.dp)
//        ) {
//            quickQuestions.forEach { q ->
//                FilterChip(
//                    selected = false,
//                    onClick = { viewModel.askQuestion(q) },
//                    label = { Text(q, fontSize = 11.sp) },
//                    colors = FilterChipDefaults.filterChipColors(
//                        containerColor = CardDark,
//                        labelColor = TextSecondary
//                    )
//                )
//            }
//        }
//
//        Spacer(Modifier.height(12.dp))
//        OutlinedTextField(
//            value = userQuestion,
//            onValueChange = { userQuestion = it },
//            modifier = Modifier.fillMaxWidth(),
//            label = { Text("Ask anything about this code...", color = TextSecondary) },
//            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
//            colors = OutlinedTextFieldDefaults.colors(
//                focusedBorderColor = PrimaryBlue,
//                unfocusedBorderColor = TextSecondary.copy(0.5f),
//                focusedContainerColor = CardDark,
//                unfocusedContainerColor = CardDark
//            ),
//            trailingIcon = {
//                IconButton(
//                    onClick = {
//                        if (userQuestion.isNotBlank()) {
//                            viewModel.askQuestion(userQuestion)
//                            userQuestion = ""
//                        }
//                    }
//                ) {
//                    Icon(Icons.Default.Send, contentDescription = null, tint = PrimaryBlue)
//                }
//            }
//        )
//    }
//}
//
//// ─────────────────────────────────────────────
////  Utility Composables
//// ─────────────────────────────────────────────
//
//@Composable
//private fun EmptyState(message: String, color: Color) {
//    Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
//        Column(horizontalAlignment = Alignment.CenterHorizontally) {
//            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(48.dp))
//            Spacer(Modifier.height(12.dp))
//            Text(message, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
//        }
//    }
//}
//
//@Composable
//private fun ErrorPanel(message: String, onRetry: () -> Unit) {
//    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
//        Column(horizontalAlignment = Alignment.CenterHorizontally) {
//            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRed, modifier = Modifier.size(56.dp))
//            Spacer(Modifier.height(16.dp))
//            Text("Something went wrong", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
//            Text(message, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
//            Spacer(Modifier.height(24.dp))
//            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
//                Icon(Icons.Default.Refresh, contentDescription = null)
//                Spacer(Modifier.width(8.dp))
//                Text("Try Again")
//            }
//        }
//    }
//}
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.runanywhere.kotlin_starter_example.debugger.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.debugger.analysis.DebugError
import com.runanywhere.kotlin_starter_example.debugger.analysis.Severity
import com.runanywhere.kotlin_starter_example.debugger.quality.IssueSeverity
import com.runanywhere.kotlin_starter_example.debugger.quality.QualityIssue
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.*
import kotlinx.coroutines.launch

// ── Theme colours matching the app's design system ──────────────────────────
private val BgDeep      = Color(0xFF080C1A)
private val BgCard      = Color(0xFF0F1629)
private val BgSurface   = Color(0xFF151D35)
private val BorderFaint = Color(0xFF1E2A4A)
private val CyanAccent  = Color(0xFF00D4FF)
private val VioletAccent= Color(0xFF7C3AED)
private val GreenAccent = Color(0xFF00C896)
private val RedAccent   = Color(0xFFFF4D6D)
private val OrangeAccent= Color(0xFFFF8C42)
private val YellowAccent= Color(0xFFFFD166)
private val TextPri     = Color(0xFFE8EAF6)
private val TextSec     = Color(0xFF7986A3)
private val CodeBg      = Color(0xFF060B14)

// ── Severity helpers ─────────────────────────────────────────────────────────
private fun severityColor(s: Severity) = when (s) {
    Severity.CRITICAL -> RedAccent
    Severity.HIGH     -> OrangeAccent
    Severity.MEDIUM   -> YellowAccent
    Severity.LOW      -> GreenAccent
}
private fun qSeverityColor(s: IssueSeverity) = when (s) {
    IssueSeverity.BLOCKER  -> RedAccent
    IssueSeverity.CRITICAL -> OrangeAccent
    IssueSeverity.MAJOR    -> YellowAccent
    IssueSeverity.MINOR    -> CyanAccent
    IssueSeverity.INFO     -> TextSec
}

// ════════════════════════════════════════════════════════════════════════════
//  ENTRY POINT
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeFinalizerScreen(
    onNavigateBack: (() -> Unit)? = null,
    modelService: ModelService = viewModel(),
    viewModel: CodeFinalizerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.processZip(context, it) }
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bmp = android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            viewModel.processImage(context, bmp)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(CyanAccent, VioletAccent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.BugReport, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text("Code Finalizer", color = TextPri, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Debug · Analyze · Fix", color = TextSec, fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = TextSec)
                        }
                    }
                },
                actions = {
                    if (state.phase is DebugPhase.Complete || state.phase is DebugPhase.Error) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(Icons.Rounded.Refresh, "Reset", tint = TextSec)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgCard)
            )
        },
        containerColor = BgDeep
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(targetState = state.phase, label = "phase", transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
            }) { phase ->
                when (phase) {
                    is DebugPhase.Idle -> IdleScreen(
                        onPickZip = { zipLauncher.launch("application/zip") },
                        onPickImage = { imageLauncher.launch("image/*") },
                        onPasteCode = { code -> viewModel.processText(context, code) }
                    )
                    is DebugPhase.ProcessingInput,
                    is DebugPhase.AnalyzingCode,
                    is DebugPhase.EvaluatingQuality -> LoadingScreen(phase)
                    is DebugPhase.Complete -> ResultScreen(state, viewModel, modelService)
                    is DebugPhase.Error -> ErrorScreen(phase.message) { viewModel.reset() }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  IDLE — INPUT SELECTION
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun IdleScreen(
    onPickZip: () -> Unit,
    onPickImage: () -> Unit,
    onPasteCode: (String) -> Unit
) {
    var pastedCode by remember { mutableStateOf("") }
    var showPaste by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // Animated hero
        val infiniteTransition = rememberInfiniteTransition(label = "hero")
        val angle by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "rot")

        Box(
            modifier = Modifier.size(100.dp).drawBehind {
                rotate(angle) {
                    drawCircle(
                        Brush.sweepGradient(listOf(CyanAccent.copy(.4f), VioletAccent.copy(.4f), CyanAccent.copy(.4f))),
                        radius = size.minDimension / 2f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(4f)
                    )
                }
            }.clip(CircleShape).background(BgCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.BugReport, null, tint = CyanAccent, modifier = Modifier.size(48.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text("Code Finalizer", color = TextPri, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Offline · 3-Phase Analysis · AI Fix", color = TextSec, fontSize = 13.sp)

        Spacer(Modifier.height(36.dp))

        // Phase badges row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhasePill("1", "Input",   CyanAccent)
            PhasePill("2", "Debug",   RedAccent)
            PhasePill("3", "Quality", YellowAccent)
            PhasePill("AI","Fix",     GreenAccent)
        }

        Spacer(Modifier.height(32.dp))

        // Input cards
        InputCard(Icons.Rounded.FolderZip, "ZIP Project", "Scan all code files in archive", CyanAccent, onPickZip)
        Spacer(Modifier.height(12.dp))
        InputCard(Icons.Rounded.Image, "Code Image", "OCR extracts code from photo", VioletAccent, onPickImage)
        Spacer(Modifier.height(12.dp))
        InputCard(Icons.Rounded.ContentPaste, "Paste Code", "Type or paste code directly", GreenAccent) { showPaste = !showPaste }

        AnimatedVisibility(showPaste, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(top = 12.dp)) {
                OutlinedTextField(
                    value = pastedCode,
                    onValueChange = { pastedCode = it },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    label = { Text("Paste code here…", color = TextSec, fontSize = 13.sp) },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, color = TextPri, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent, unfocusedBorderColor = BorderFaint,
                        focusedContainerColor = CodeBg, unfocusedContainerColor = CodeBg
                    )
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { if (pastedCode.isNotBlank()) onPasteCode(pastedCode) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(12.dp),
                    enabled = pastedCode.isNotBlank()
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = BgDeep, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Analyze Code", color = BgDeep, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PhasePill(number: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(color.copy(.15f)).border(1.dp, color.copy(.5f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { Text(number, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        Text(label, color = TextSec, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun InputCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, BorderFaint)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPri, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(sub, color = TextSec, fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = TextSec, modifier = Modifier.size(20.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  LOADING
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun LoadingScreen(phase: DebugPhase) {
    val (icon, title, sub) = when (phase) {
        is DebugPhase.ProcessingInput  -> Triple(Icons.Rounded.FolderOpen,    "Processing Input",   "Extracting & normalizing code…")
        is DebugPhase.AnalyzingCode    -> Triple(Icons.Rounded.Search,         "Analyzing Code",     "Syntax & semantic checks…")
        is DebugPhase.EvaluatingQuality-> Triple(Icons.Rounded.Analytics,      "Evaluating Quality", "Running PMD · Pylint · ESLint rules…")
        else                           -> Triple(Icons.Rounded.HourglassTop,   "Working…",           "")
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanAccent, modifier = Modifier.size(72.dp), strokeWidth = 3.dp)
                Icon(icon, null, tint = CyanAccent, modifier = Modifier.size(30.dp))
            }
            Text(title, color = TextPri, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = TextSec, fontSize = 13.sp)
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  RESULT SCREEN — tabs
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ResultScreen(
    state: CodeFinalizerState,
    viewModel: CodeFinalizerViewModel,
    modelService: ModelService
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val analysis = viewModel.selectedAnalysis
    val quality  = viewModel.selectedQuality
    val file     = viewModel.selectedFile

    val errorCount   = analysis?.errors?.size ?: 0
    val qualityScore = quality?.overallScore ?: 0

    Column(Modifier.fillMaxSize()) {

        // File selector (if multiple files)
        if (state.loadedFiles.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = state.selectedFileIndex,
                containerColor = BgCard, contentColor = CyanAccent, edgePadding = 8.dp
            ) {
                state.loadedFiles.forEachIndexed { idx, f ->
                    Tab(
                        selected = state.selectedFileIndex == idx,
                        onClick = { viewModel.selectFile(idx) },
                        text = {
                            Text(f.fileName.substringAfterLast('/').take(18),
                                color = if (state.selectedFileIndex == idx) CyanAccent else TextSec,
                                fontSize = 12.sp)
                        }
                    )
                }
            }
        }

        // Summary strip
        if (file != null) {
            Row(
                modifier = Modifier.fillMaxWidth().background(BgCard)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(file.fileName.substringAfterLast('/'), color = TextPri, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${file.language.displayName} · ${file.lineCount} lines", color = TextSec, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScorePill("$errorCount err", if (errorCount > 0) RedAccent else GreenAccent)
                    ScorePill("$qualityScore/100", when {
                        qualityScore >= 80 -> GreenAccent
                        qualityScore >= 50 -> YellowAccent
                        else               -> RedAccent
                    })
                }
            }
        }

        // Tab row
        val tabs = listOf(
            "Errors ($errorCount)" to Icons.Rounded.BugReport,
            "Quality ($qualityScore)" to Icons.Rounded.Analytics,
            "AI Chat" to Icons.Rounded.SmartToy
        )
        TabRow(selectedTabIndex = selectedTab, containerColor = BgCard, contentColor = CyanAccent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]), color = CyanAccent, height = 2.dp)
            }
        ) {
            tabs.forEachIndexed { idx, (label, icon) ->
                Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }) {
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(icon, null, tint = if (selectedTab == idx) CyanAccent else TextSec, modifier = Modifier.size(16.dp))
                        Text(label, color = if (selectedTab == idx) CyanAccent else TextSec, fontSize = 12.sp)
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> ErrorsTab(analysis?.errors ?: emptyList())
            1 -> QualityTab(quality)
            2 -> ChatTab(state, viewModel, modelService)
        }
    }
}

@Composable
private fun ScorePill(text: String, color: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(.15f))
            .border(1.dp, color.copy(.4f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

// ════════════════════════════════════════════════════════════════════════════
//  ERRORS TAB
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ErrorsTab(errors: List<DebugError>) {
    if (errors.isEmpty()) {
        EmptyState(Icons.Rounded.CheckCircle, "No errors found!", GreenAccent, "Your code passed syntax & semantic checks.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(errors) { error -> ErrorCard(error) }
    }
}

@Composable
private fun ErrorCard(error: DebugError) {
    val color = severityColor(error.severity)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, color.copy(.3f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(error.severity.name, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(color.copy(.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                Spacer(Modifier.width(8.dp))
                Text("Line ${error.lineNumber}", color = TextSec, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("${(error.confidence * 100).toInt()}%", color = TextSec, fontSize = 10.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(error.message, color = TextPri, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(error.errorType.name.replace("_", " "), color = TextSec, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            if (error.errorContext.isNotBlank()) {
                Text(error.errorContext.take(80), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    color = Color(0xFFB0BEC5), overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        .background(CodeBg, RoundedCornerShape(6.dp)).padding(8.dp))
            }
            if (error.suggestion.isNotBlank()) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lightbulb, null, tint = YellowAccent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(error.suggestion, color = YellowAccent, fontSize = 11.sp)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  QUALITY TAB
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun QualityTab(quality: com.runanywhere.kotlin_starter_example.debugger.quality.QualityReport?) {
    if (quality == null) {
        EmptyState(Icons.Rounded.HourglassTop, "No quality data", TextSec, "Run analysis first.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { QualityOverviewCard(quality) }
        item { MetricsCard(quality) }
        if (quality.issues.isEmpty()) {
            item { EmptyState(Icons.Rounded.CheckCircle, "No quality issues!", GreenAccent, "Code meets all quality standards.") }
        } else {
            items(quality.issues) { issue -> QualityIssueCard(issue) }
        }
    }
}

@Composable
private fun QualityOverviewCard(quality: com.runanywhere.kotlin_starter_example.debugger.quality.QualityReport) {
    val score = quality.overallScore
    val color = when { score >= 80 -> GreenAccent; score >= 50 -> YellowAccent; else -> RedAccent }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard), border = BorderStroke(1.dp, color.copy(.3f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { score / 100f }, modifier = Modifier.fillMaxSize(),
                    color = color, trackColor = color.copy(.15f), strokeWidth = 5.dp)
                Text("$score", color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Quality Score", color = TextPri, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${quality.issues.size} issues · ${quality.analysisTimeMs}ms", color = TextSec, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                val label = when { score >= 80 -> "Excellent" ; score >= 60 -> "Good" ; score >= 40 -> "Fair" ; else -> "Needs Work" }
                Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MetricsCard(quality: com.runanywhere.kotlin_starter_example.debugger.quality.QualityReport) {
    val m = quality.complexityMetrics
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard), border = BorderStroke(1.dp, BorderFaint)) {
        Column(Modifier.padding(16.dp)) {
            Text("Complexity Metrics", color = TextPri, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricChip("Cyclomatic", m.cyclomaticComplexity.toString(), if (m.cyclomaticComplexity > 10) RedAccent else GreenAccent)
                MetricChip("Lines", m.linesOfCode.toString(), CyanAccent)
                MetricChip("Nesting", m.maxNestingDepth.toString(), if (m.maxNestingDepth > 4) YellowAccent else GreenAccent)
                MetricChip("Avg Func", "${m.avgFunctionLength}L", if (m.avgFunctionLength > 50) OrangeAccent else GreenAccent)
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = TextSec, fontSize = 10.sp)
    }
}

@Composable
private fun QualityIssueCard(issue: QualityIssue) {
    val color = qSeverityColor(issue.severity)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard), border = BorderStroke(1.dp, BorderFaint)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.width(3.dp).height(44.dp).background(color, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(issue.severity.label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(color.copy(.12f), RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(issue.ruleSource, color = TextSec, fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    Text("L${issue.lineNumber}", color = TextSec, fontSize = 10.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(issue.improvementHint, color = TextPri, fontSize = 12.sp)
                if (issue.lineContext.isNotBlank()) {
                    Text(issue.lineContext.take(55), fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        color = TextSec, modifier = Modifier.padding(top = 4.dp), overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  AI CHAT TAB
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ChatTab(
    state: CodeFinalizerState,
    viewModel: CodeFinalizerViewModel,
    modelService: ModelService
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll on new message
    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) listState.animateScrollToItem(state.chatMessages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {

        // ── Model loader (mirrors ChatScreen exactly) ────────────────────
        if (!modelService.isLLMLoaded) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                ModelLoaderWidget(
                    modelName = "SmolLM2 360M",
                    isDownloading = modelService.isLLMDownloading,
                    isLoading = modelService.isLLMLoading,
                    isLoaded = modelService.isLLMLoaded,
                    downloadProgress = modelService.llmDownloadProgress,
                    onLoadClick = { modelService.downloadAndLoadLLM() }
                )
                modelService.errorMessage?.let { err ->
                    Text(err, color = RedAccent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                // Info card
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard), border = BorderStroke(1.dp, BorderFaint)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Load the LLM model above to chat about your code errors and get AI-powered fix suggestions.",
                            color = TextSec, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
        }

        // ── Quick action chips (only when model loaded and code analyzed) ─
        if (modelService.isLLMLoaded && viewModel.selectedFile != null) {
            Row(
                modifier = Modifier.fillMaxWidth().background(BgCard).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickChip("Fix errors", Icons.Rounded.BugReport, RedAccent) { viewModel.askAboutErrors(modelService) }
                QuickChip("Quality tips", Icons.Rounded.Analytics, YellowAccent) { viewModel.askAboutQuality(modelService) }
                QuickChip("Write fix", Icons.Rounded.AutoFixHigh, GreenAccent) { viewModel.askForFix(modelService) }
            }
        }

        // ── Messages list ─────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (state.chatMessages.isEmpty() && modelService.isLLMLoaded) {
                item { ChatWelcome() }
            }
            items(state.chatMessages) { msg -> ChatBubble(msg) }
            if (state.isGeneratingChat) {
                item { TypingIndicator() }
            }
        }

        // ── Input bar ─────────────────────────────────────────────────────
        if (modelService.isLLMLoaded) {
            Surface(Modifier.fillMaxWidth(), color = BgCard, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                    TextField(
                        value = state.chatInput,
                        onValueChange = { viewModel.updateChatInput(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask about errors, get fixes…", color = TextSec, fontSize = 13.sp) },
                        readOnly = state.isGeneratingChat,
                        textStyle = LocalTextStyle.current.copy(color = TextPri, fontSize = 13.sp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = BgSurface, unfocusedContainerColor = BgSurface,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (!state.isGeneratingChat && state.chatInput.isNotBlank()) {
                                viewModel.sendChatMessage(modelService, state.chatInput)
                                scope.launch { if (state.chatMessages.isNotEmpty()) listState.animateScrollToItem(state.chatMessages.size) }
                            }
                        },
                        containerColor = if (state.chatInput.isBlank() || state.isGeneratingChat) TextSec else CyanAccent,
                        modifier = Modifier.size(48.dp)
                    ) {
                        if (state.isGeneratingChat) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = BgDeep, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.Send, null, tint = BgDeep)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(.12f))
            .border(1.dp, color.copy(.35f), RoundedCornerShape(20.dp)).clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ChatWelcome() {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.SmartToy, null, tint = CyanAccent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("AI Debug Assistant", color = TextPri, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text("Ask me about errors found in your code,\nget fix suggestions, or explain logic.",
            color = TextSec, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

@Composable
private fun ChatBubble(msg: DebugChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!msg.isUser) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(CyanAccent.copy(.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SmartToy, null, tint = CyanAccent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier.widthIn(max = 270.dp)
                .clip(RoundedCornerShape(
                    topStart = if (msg.isUser) 16.dp else 4.dp,
                    topEnd = if (msg.isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp, bottomEnd = 16.dp
                ))
                .background(if (msg.isUser) CyanAccent.copy(.85f) else BgSurface)
                .padding(12.dp)
        ) {
            Text(msg.text, color = if (msg.isUser) BgDeep else TextPri, fontSize = 13.sp, lineHeight = 19.sp)
        }
        if (msg.isUser) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(30.dp).clip(CircleShape).background(VioletAccent.copy(.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, null, tint = VioletAccent, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(CyanAccent.copy(.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.SmartToy, null, tint = CyanAccent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(BgSurface).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val alpha by infiniteTransition.animateFloat(0.3f, 1f,
                    infiniteRepeatable(tween(600, delayMillis = index * 150, easing = EaseInOut), RepeatMode.Reverse), label = "dot$index")
                Box(Modifier.size(7.dp).clip(CircleShape).background(CyanAccent.copy(alpha)))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  SHARED UTILITY COMPOSABLES
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    color: Color,
    subtitle: String
) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = TextSec, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = RedAccent, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Something went wrong", color = TextPri, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(message, color = TextSec, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = CyanAccent), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.Refresh, null, tint = BgDeep, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try Again", color = BgDeep, fontWeight = FontWeight.Bold)
            }
        }
    }
}

