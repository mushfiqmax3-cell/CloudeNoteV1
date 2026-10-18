package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.NoteDraftEntity
import com.example.data.NoteModel
import com.example.data.NoteSection
import com.example.network.AiActionType
import com.example.ui.theme.LocalAppThemeMode
import com.example.util.StorageChunking
import com.example.util.TextNormalizer
import java.util.UUID

@Composable
fun NoteEditorScreen(
    existingNote: NoteModel?,
    initialFolder: String,
    availableFolders: List<String>,
    onBack: () -> Unit,
    onOpenThemeSelector: () -> Unit,
    onCreateNewFolder: (String, (String) -> Unit) -> Unit,
    onScheduleDraft: (String, String, String, List<NoteSection>) -> Unit,
    onLoadDraft: suspend (String) -> NoteDraftEntity?,
    onDiscardDraft: (String) -> Unit,
    onRunAiAssistant: (AiActionType, String, (Result<String>) -> Unit) -> Unit,
    onSaveNote: (
        noteId: String?,
        title: String,
        folder: String,
        sections: List<NoteSection>,
        isFavorite: Boolean,
        isTrashed: Boolean,
        pinHash: String?
    ) -> Unit
) {
    val theme = LocalAppThemeMode.current
    val draftKey = remember(existingNote?.id) { existingNote?.id ?: "draft_new_note" }

    var title by remember(existingNote?.id) { mutableStateOf(existingNote?.title ?: "") }
    var selectedFolder by remember(existingNote?.id) {
        mutableStateOf(existingNote?.folder ?: initialFolder.ifBlank { "সাধারণ" })
    }
    var sections by remember(existingNote?.id) {
        mutableStateOf(
            existingNote?.sections?.ifEmpty {
                listOf(NoteSection(id = "sec_1", title = "", content = ""))
            } ?: listOf(NoteSection(id = "sec_1", title = "", content = ""))
        )
    }

    var focusedSectionIndex by remember { mutableIntStateOf(0) }
    var showFolderDropdown by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var recoveredDraft by remember { mutableStateOf<NoteDraftEntity?>(null) }

    // Per-section AI Assistant dialog state
    var aiTargetSectionIndex by remember { mutableStateOf<Int?>(null) }
    var isAiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }

    // Check for unsaved local draft on open
    LaunchedEffect(draftKey) {
        val draft = onLoadDraft(draftKey)
        if (draft != null && draft.updatedAt != existingNote?.updatedAt) {
            recoveredDraft = draft
        }
    }

    // 1-second debounced draft save whenever title, folder, or sections change
    LaunchedEffect(title, selectedFolder, sections) {
        if (title.isNotBlank() || sections.any { it.content.isNotBlank() || it.title.isNotBlank() }) {
            onScheduleDraft(draftKey, title, selectedFolder, sections)
        }
    }

    BackHandler {
        onBack()
    }

    fun appendFormatSnippetToActiveSection(snippet: String) {
        val idx = focusedSectionIndex.coerceIn(0, sections.lastIndex)
        val current = sections[idx]
        val sep = if (current.content.isEmpty() || current.content.endsWith("\n")) "" else "\n"
        val updated = current.copy(content = current.content + sep + snippet)
        sections = sections.toMutableList().also { it[idx] = updated }
    }

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
                            .testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                            tint = theme.text
                        )
                    }

                    Text(
                        text = if (existingNote == null) "নতুন নোট" else "নোট এডিটর",
                        color = theme.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )

                    // Smart AI Paste & Bengali NFC Cleaner button
                    IconButton(
                        onClick = {
                            title = TextNormalizer.normalizeAndClean(title, cleanAiMathAndTables = true)
                            sections = sections.map { sec ->
                                sec.copy(
                                    title = TextNormalizer.normalizeAndClean(sec.title, cleanAiMathAndTables = true),
                                    content = TextNormalizer.normalizeAndClean(sec.content, cleanAiMathAndTables = true)
                                )
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("editor_clean_unicode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "বাংলা ইউনিকোড ও এআই পেস্ট ক্লিনার",
                            tint = theme.text
                        )
                    }

                    IconButton(
                        onClick = onOpenThemeSelector,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("editor_theme_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "থিম",
                            tint = theme.text
                        )
                    }

                    Button(
                        onClick = {
                            onSaveNote(
                                existingNote?.id,
                                title,
                                selectedFolder,
                                sections,
                                existingNote?.isFavorite ?: false,
                                existingNote?.isTrashed ?: false,
                                existingNote?.pinHash
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.accent,
                            contentColor = theme.accentFg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .padding(end = 6.dp)
                            .testTag("editor_save_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("সেভ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // One-Tap Draft Recovery Banner
                if (recoveredDraft != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.bgAlt)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("draft_recovery_banner")
                    ) {
                        Text(
                            text = "অসংরক্ষিত ড্রাফট পাওয়া গেছে",
                            color = theme.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onDiscardDraft(draftKey)
                                    recoveredDraft = null
                                }
                            ) {
                                Text("বাতিল", color = theme.text, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    val d = recoveredDraft!!
                                    title = d.title
                                    selectedFolder = d.folder
                                    sections = StorageChunking.deserializeSections(d.sectionsJson)
                                    recoveredDraft = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.accent,
                                    contentColor = theme.accentFg
                                )
                            ) {
                                Text("পুনরুদ্ধার", fontSize = 11.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = theme.border, thickness = 1.dp)
            }
        },
        bottomBar = {
            // Mobile Touch Formatting Toolbar (Bold, Italic, Headings, KaTeX Math, GFM Tables, Lists, Code)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surface)
                    .border(1.dp, theme.border)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                FormatChip("B", "বোল্ড") { appendFormatSnippetToActiveSection("**বোল্ড টেক্সট**") }
                FormatChip("I", "ইটালিক") { appendFormatSnippetToActiveSection("*ইটালিক*") }
                FormatChip("H1", "হেডিং ১") { appendFormatSnippetToActiveSection("# শিরোনাম ১") }
                FormatChip("H2", "হেডিং ২") { appendFormatSnippetToActiveSection("## শিরোনাম ২") }
                FormatChip("∑ $", "ইনলাইন গণিত") { appendFormatSnippetToActiveSection("\$E = mc^2\$") }
                FormatChip("$$", "ব্লক গণিত") {
                    appendFormatSnippetToActiveSection("$$\n\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}\n$$")
                }
                FormatChip("টেবিল", "GFM টেবিল") {
                    appendFormatSnippetToActiveSection(
                        "| কলাম ১ | কলাম ২ |\n| :--- | :--- |\n| তথ্য ১ | তথ্য ২ |"
                    )
                }
                FormatChip("তালিকা", "বুলেট লিস্ট") {
                    appendFormatSnippetToActiveSection("- পয়েন্ট ১\n- পয়েন্ট ২")
                }
                FormatChip("</> কোড", "কোড ব্লক") {
                    appendFormatSnippetToActiveSection("```kotlin\n// কোড লিখুন\n```")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Note Title & Metadata Bar with Working Folder Dropdown + "+ ফোল্ডার" button
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.surface)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("নোটের শিরোনাম") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_title_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Working Folder Selector Dropdown + "+ ফোল্ডার" button right inside the editor
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.bgAlt)
                                    .border(1.dp, theme.border, RoundedCornerShape(10.dp))
                                    .clickable { showFolderDropdown = true }
                                    .padding(horizontal = 12.dp)
                                    .testTag("editor_folder_selector")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = theme.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedFolder,
                                    color = theme.text,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Folder",
                                    tint = theme.text
                                )
                            }

                            DropdownMenu(
                                expanded = showFolderDropdown,
                                onDismissRequest = { showFolderDropdown = false },
                                modifier = Modifier.background(theme.surface)
                            ) {
                                availableFolders.forEach { folderOption ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = folderOption,
                                                color = theme.text,
                                                fontWeight = if (folderOption == selectedFolder) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            selectedFolder = folderOption
                                            showFolderDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // "+ ফোল্ডার" button right inside the editor
                        OutlinedButton(
                            onClick = { showCreateFolderDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("editor_new_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = theme.text,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+ ফোল্ডার",
                                color = theme.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Multi-Section Cards with Reordering (Up/Down), Word Count, and Gemini AI Assistant
            itemsIndexed(sections, key = { _, sec -> sec.id }) { index, section ->
                SectionEditorCard(
                    index = index,
                    totalSections = sections.size,
                    section = section,
                    onFocus = { focusedSectionIndex = index },
                    onTitleChange = { newTitle ->
                        sections = sections.toMutableList().also {
                            it[index] = section.copy(title = newTitle)
                        }
                    },
                    onContentChange = { newContent ->
                        sections = sections.toMutableList().also {
                            it[index] = section.copy(content = newContent)
                        }
                    },
                    onMoveUp = {
                        if (index > 0) {
                            val mutable = sections.toMutableList()
                            val tmp = mutable[index - 1]
                            mutable[index - 1] = mutable[index]
                            mutable[index] = tmp
                            sections = mutable
                            focusedSectionIndex = index - 1
                        }
                    },
                    onMoveDown = {
                        if (index < sections.lastIndex) {
                            val mutable = sections.toMutableList()
                            val tmp = mutable[index + 1]
                            mutable[index + 1] = mutable[index]
                            mutable[index] = tmp
                            sections = mutable
                            focusedSectionIndex = index + 1
                        }
                    },
                    onDeleteSection = {
                        if (sections.size > 1) {
                            sections = sections.toMutableList().also { it.removeAt(index) }
                            focusedSectionIndex = (focusedSectionIndex - 1).coerceAtLeast(0)
                        }
                    },
                    onOpenAiAssistant = {
                        aiTargetSectionIndex = index
                        aiError = null
                    }
                )
            }

            // Add New Section Button
            item {
                OutlinedButton(
                    onClick = {
                        val newSec = NoteSection(
                            id = "sec_" + UUID.randomUUID().toString().take(8),
                            title = "",
                            content = ""
                        )
                        sections = sections + newSec
                        focusedSectionIndex = sections.lastIndex
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("editor_add_section_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = theme.text)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "নতুন সেকশন যুক্ত করুন",
                        color = theme.text,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Inline "+ ফোল্ডার" Modal inside NoteEditor
    if (showCreateFolderDialog) {
        var newFolderText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showCreateFolderDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = theme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.border, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "নতুন ফোল্ডার তৈরি করুন",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.text,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFolderText,
                        onValueChange = { newFolderText = it },
                        label = { Text("ফোল্ডারের নাম") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_modal_folder_input")
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCreateFolderDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("বাতিল", color = theme.text)
                        }
                        Button(
                            onClick = {
                                val cleaned = newFolderText.trim()
                                if (cleaned.isNotEmpty()) {
                                    onCreateNewFolder(cleaned) { createdName ->
                                        selectedFolder = createdName
                                    }
                                    showCreateFolderDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.accent,
                                contentColor = theme.accentFg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_modal_folder_save")
                        ) {
                            Text("তৈরি ও নির্বাচন")
                        }
                    }
                }
            }
        }
    }

    // Per-Section Gemini AI Assistant Modal
    if (aiTargetSectionIndex != null) {
        val secIdx = aiTargetSectionIndex!!
        val targetSec = sections.getOrNull(secIdx)
        if (targetSec != null) {
            Dialog(onDismissRequest = { if (!isAiLoading) aiTargetSectionIndex = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = theme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Gemini AI অ্যাসিস্ট্যান্ট (সেকশন ${TextNormalizer.toBengaliDigits(secIdx + 1)})",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.text,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (isAiLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = theme.accent,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("প্রসেস হচ্ছে...", color = theme.text)
                            }
                        } else {
                            AiActionType.entries.forEach { action ->
                                OutlinedButton(
                                    onClick = {
                                        isAiLoading = true
                                        aiError = null
                                        onRunAiAssistant(action, targetSec.content) { result ->
                                            isAiLoading = false
                                            result.onSuccess { transformed ->
                                                sections = sections.toMutableList().also {
                                                    it[secIdx] = targetSec.copy(content = transformed)
                                                }
                                                aiTargetSectionIndex = null
                                            }.onFailure { err ->
                                                aiError = err.message
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        tint = theme.text,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(action.labelBn, color = theme.text, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (aiError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = aiError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { aiTargetSectionIndex = null },
                            enabled = !isAiLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("বন্ধ করুন", color = theme.text)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionEditorCard(
    index: Int,
    totalSections: Int,
    section: NoteSection,
    onFocus: () -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDeleteSection: () -> Unit,
    onOpenAiAssistant: () -> Unit
) {
    val theme = LocalAppThemeMode.current
    val wordCount = remember(section.title, section.content) {
        TextNormalizer.countWords(section.title) + TextNormalizer.countWords(section.content)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surface)
            .border(1.dp, theme.border, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("section_card_$index")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "সেকশন ${TextNormalizer.toBengaliDigits(index + 1)} • ${TextNormalizer.toBengaliDigits(wordCount)} শব্দ",
                color = theme.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Gemini AI Assistant button per section
            IconButton(
                onClick = onOpenAiAssistant,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("section_ai_btn_$index")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "Gemini AI",
                    tint = theme.accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (totalSections > 1) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "উপরে নিন",
                        tint = if (index > 0) theme.text else theme.muted,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalSections - 1,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "নিচে নিন",
                        tint = if (index < totalSections - 1) theme.text else theme.muted,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteSection,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "সেকশন মুছুন",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = section.title,
            onValueChange = onTitleChange,
            placeholder = { Text("সেকশন শিরোনাম (ঐচ্ছিক)", fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocus() }
                .testTag("section_title_input_$index")
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = section.content,
            onValueChange = onContentChange,
            placeholder = { Text("নোট লিখুন (বাংলা, Markdown, \$গণিত\$, টেবিল)...", fontSize = 14.sp) },
            minLines = 6,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocus() }
                .testTag("section_content_input_$index")
        )
    }
}

@Composable
private fun FormatChip(
    label: String,
    contentDesc: String,
    onClick: () -> Unit
) {
    val theme = LocalAppThemeMode.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.bgAlt)
            .border(1.dp, theme.border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = label,
            color = theme.text,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
