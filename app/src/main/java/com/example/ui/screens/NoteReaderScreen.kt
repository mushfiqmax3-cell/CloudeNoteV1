package com.example.ui.screens

import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.NoteModel
import com.example.data.NoteSection
import com.example.data.NoteVersionSnapshot
import com.example.ui.components.MarkdownParser
import com.example.ui.components.RichMarkdownContent
import com.example.ui.theme.LocalAppThemeMode
import com.example.ui.theme.ReaderFontOption
import com.example.util.TextNormalizer
import kotlinx.coroutines.launch
import java.util.Locale

enum class ExportFormat(val label: String, val ext: String) {
    PDF("PDF ডকুমেন্ট (.pdf)", "pdf"),
    WORD("MS Word (.doc)", "doc"),
    MARKDOWN("Markdown (.md)", "md"),
    TXT("Plain Text (.txt)", "txt")
}

@Composable
fun NoteReaderScreen(
    note: NoteModel,
    readerFont: ReaderFontOption,
    readerFontSizeSp: Int,
    onSelectReaderFont: (ReaderFontOption) -> Unit,
    onChangeReaderFontSize: (Int) -> Unit,
    onBack: () -> Unit,
    onEditNote: () -> Unit,
    onOpenThemeSelector: () -> Unit,
    onOpenPinSettings: () -> Unit,
    onReplaceInNote: (List<NoteSection>) -> Unit,
    onExportFormat: (ExportFormat) -> Unit,
    onLoadVersionHistory: suspend (String) -> List<NoteVersionSnapshot>,
    onRestoreVersionSnapshot: (NoteVersionSnapshot) -> Unit
) {
    val theme = LocalAppThemeMode.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showToc by remember { mutableStateOf(false) }
    var showTypographyBar by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var isFullScreenReading by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // Bengali TTS (bn-BD) Audio Reader
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val bnLocale = Locale.forLanguageTag("bn-BD")
                val res = engine?.setLanguage(bnLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine?.language = Locale.getDefault()
                }
            }
        }
        ttsEngine = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    fun toggleSpeech() {
        val engine = ttsEngine ?: return
        if (isSpeaking) {
            engine.stop()
            isSpeaking = false
        } else {
            val fullSpeech = buildString {
                append(note.title).append(". ")
                note.sections.forEach { sec ->
                    if (sec.title.isNotBlank()) append(sec.title).append(". ")
                    append(sec.content.replace(Regex("[#*`>\\$\\|]"), " ")).append(". ")
                }
            }
            engine.speak(fullSpeech.take(3900), TextToSpeech.QUEUE_FLUSH, null, "cn_tts_utterance")
            isSpeaking = true
        }
    }

    // Hardware / Gesture Back Handler
    BackHandler {
        if (isFullScreenReading) {
            isFullScreenReading = false
        } else if (showFindReplace) {
            showFindReplace = false
        } else if (showTypographyBar) {
            showTypographyBar = false
        } else {
            onBack()
        }
    }

    val tocItems = remember(note.sections) { MarkdownParser.extractToc(note.sections) }

    Scaffold(
        containerColor = theme.bg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surface)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                            tint = theme.text
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = note.title,
                            color = theme.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = theme.muted,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${note.folder} • ${TextNormalizer.toBengaliDigits(note.totalWords)} শব্দ",
                                color = theme.muted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = { toggleSpeech() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("reader_tts_button")
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "বাংলা অডিও পাঠ (TTS)",
                            tint = if (isSpeaking) MaterialTheme.colorScheme.error else theme.text
                        )
                    }

                    IconButton(
                        onClick = { isFullScreenReading = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("reader_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "ফুল-স্ক্রিন রিডিং মোড",
                            tint = theme.text
                        )
                    }

                    IconButton(
                        onClick = onOpenThemeSelector,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("reader_theme_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "থিম পরিবর্তন",
                            tint = theme.text
                        )
                    }
                }

                // Expandable Typography & Font Switcher Bar
                AnimatedVisibility(visible = showTypographyBar) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.bgAlt)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ফন্ট ও সাইজ (${TextNormalizer.toBengaliDigits(readerFontSizeSp)} px)",
                                color = theme.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            IconButton(
                                onClick = { showTypographyBar = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.muted)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaderFontOption.entries.forEach { fontOpt ->
                                val selected = fontOpt == readerFont
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) theme.accent else theme.surface)
                                        .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                                        .clickable { onSelectReaderFont(fontOpt) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("reader_font_${fontOpt.id}")
                                ) {
                                    Text(
                                        text = fontOpt.label,
                                        color = if (selected) theme.accentFg else theme.text,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Slider(
                            value = readerFontSizeSp.toFloat(),
                            onValueChange = { onChangeReaderFontSize(it.toInt()) },
                            valueRange = 14f..28f,
                            steps = 13,
                            modifier = Modifier.testTag("reader_fontsize_slider")
                        )
                    }
                }

                // Expandable Cross-Section Find & Replace Bar
                AnimatedVisibility(visible = showFindReplace) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.bgAlt)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = findQuery,
                                onValueChange = { findQuery = it },
                                placeholder = { Text("খুঁজুন...", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reader_find_input")
                            )
                            OutlinedTextField(
                                value = replaceQuery,
                                onValueChange = { replaceQuery = it },
                                placeholder = { Text("পরিবর্তন...", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reader_replace_input")
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    findQuery = ""
                                    replaceQuery = ""
                                    showFindReplace = false
                                }
                            ) {
                                Text("বন্ধ করুন", color = theme.text, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (findQuery.isNotBlank()) {
                                        val updatedSections = note.sections.map { sec ->
                                            sec.copy(
                                                title = sec.title.replace(findQuery, replaceQuery, ignoreCase = false),
                                                content = sec.content.replace(findQuery, replaceQuery, ignoreCase = false)
                                            )
                                        }
                                        onReplaceInNote(updatedSections)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.accent,
                                    contentColor = theme.accentFg
                                ),
                                modifier = Modifier.testTag("reader_replace_all_button")
                            ) {
                                Text("সব পরিবর্তন করুন", fontSize = 12.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = theme.border, thickness = 1.dp)
            }
        },
        bottomBar = {
            // Thumb-friendly Bottom Action Bar (min-h-[48px] touch targets)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surface)
                    .border(1.dp, theme.border)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(
                    onClick = { showToc = !showToc },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("reader_toc_toggle")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "সূচিপত্র",
                        tint = if (showToc) theme.accent else theme.text
                    )
                }

                IconButton(
                    onClick = { showTypographyBar = !showTypographyBar },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("reader_typography_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFormat,
                        contentDescription = "ফন্ট ও সাইজ",
                        tint = if (showTypographyBar) theme.accent else theme.text
                    )
                }

                IconButton(
                    onClick = { showFindReplace = !showFindReplace },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("reader_find_replace_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.FindReplace,
                        contentDescription = "খুঁজুন ও পরিবর্তন",
                        tint = if (showFindReplace) theme.accent else theme.text
                    )
                }

                IconButton(
                    onClick = onOpenPinSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("reader_pin_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "পিন লক",
                        tint = if (!note.pinHash.isNullOrBlank()) theme.accent else theme.text
                    )
                }

                IconButton(
                    onClick = { showHistoryDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("reader_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "ভার্সন হিস্ট্রি",
                        tint = theme.text
                    )
                }

                IconButton(
                    onClick = { showExportDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("reader_export_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "এক্সপোর্ট",
                        tint = theme.text
                    )
                }

                Button(
                    onClick = onEditNote,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accent,
                        contentColor = theme.accentFg
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("reader_edit_note_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "এডিট",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("এডিট", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Collapsible Hierarchical Table of Contents (সূচিপত্র)
            if (tocItems.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.surface)
                            .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showToc = !showToc }
                        ) {
                            Text(
                                text = "সূচিপত্র (${TextNormalizer.toBengaliDigits(tocItems.size)})",
                                color = theme.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Icon(
                                imageVector = if (showToc) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = theme.muted
                            )
                        }

                        AnimatedVisibility(visible = showToc) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tocItems.forEach { item ->
                                    val indent = ((item.level - 1) * 12).dp
                                    Text(
                                        text = "• ${item.title}",
                                        color = theme.text,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = indent)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(item.sectionIndex + 1)
                                                }
                                            }
                                            .padding(vertical = 4.dp, horizontal = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Render Multi-Section Cards
            itemsIndexed(note.sections, key = { _, sec -> sec.id }) { index, section ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    if (section.title.isNotBlank() || note.sections.size > 1) {
                        Text(
                            text = section.title.ifBlank { "সেকশন ${TextNormalizer.toBengaliDigits(index + 1)}" },
                            fontFamily = readerFont.toFontFamily(),
                            fontSize = (readerFontSizeSp + 2).sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.text
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = theme.border)
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    RichMarkdownContent(
                        markdown = section.content,
                        fontFamily = readerFont.toFontFamily(),
                        fontSizeSp = readerFontSizeSp,
                        highlightQuery = findQuery
                    )
                }
            }
        }
    }

    // Full-Screen Reading Mode Modal (ReadingModeModal)
    if (isFullScreenReading) {
        Dialog(
            onDismissRequest = { isFullScreenReading = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.bg)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = note.title,
                                fontFamily = readerFont.toFontFamily(),
                                fontSize = (readerFontSizeSp + 6).sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.text,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { isFullScreenReading = false },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("exit_fullscreen_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "ফুল-স্ক্রিন বন্ধ করুন",
                                    tint = theme.text
                                )
                            }
                        }
                        HorizontalDivider(color = theme.border, modifier = Modifier.padding(top = 8.dp))
                    }

                    itemsIndexed(note.sections) { _, sec ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (sec.title.isNotBlank()) {
                                Text(
                                    text = sec.title,
                                    fontFamily = readerFont.toFontFamily(),
                                    fontSize = (readerFontSizeSp + 3).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.text
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            RichMarkdownContent(
                                markdown = sec.content,
                                fontFamily = readerFont.toFontFamily(),
                                fontSizeSp = readerFontSizeSp,
                                highlightQuery = findQuery
                            )
                        }
                    }
                }
            }
        }
    }

    // 4-Format Export Dialog (PDF, MS Word .doc, Markdown .md, Plain Text .txt)
    if (showExportDialog) {
        Dialog(onDismissRequest = { showExportDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = theme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.border, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "নোট এক্সপোর্ট ফরম্যাট",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.text,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ExportFormat.entries.forEach { fmt ->
                        OutlinedButton(
                            onClick = {
                                showExportDialog = false
                                onExportFormat(fmt)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .height(48.dp)
                                .testTag("export_format_${fmt.ext}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = theme.text,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = fmt.label,
                                color = theme.text,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // 25-Snapshot Local Version History Modal
    if (showHistoryDialog) {
        var snapshots by remember { mutableStateOf<List<NoteVersionSnapshot>>(emptyList()) }
        LaunchedEffect(note.id) {
            snapshots = onLoadVersionHistory(note.id)
        }

        Dialog(onDismissRequest = { showHistoryDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = theme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.border, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "ভার্সন হিস্ট্রি (সর্বোচ্চ ২৫ স্ন্যাপশট)",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.text,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (snapshots.isEmpty()) {
                        Text(
                            text = "কোনো পূর্ববর্তী ভার্সন নেই",
                            color = theme.muted,
                            fontSize = 13.sp
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(snapshots) { _, snap ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.bgAlt)
                                        .border(1.dp, theme.border, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = snap.title,
                                            color = theme.text,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = snap.timestamp.take(19).replace("T", " "),
                                            color = theme.muted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            showHistoryDialog = false
                                            onRestoreVersionSnapshot(snap)
                                        }
                                    ) {
                                        Text("রিস্টোর", color = theme.text, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showHistoryDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("বন্ধ করুন", color = theme.text)
                    }
                }
            }
        }
    }
}
