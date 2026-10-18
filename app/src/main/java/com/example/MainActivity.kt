package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NoteModel
import com.example.ui.components.NavigationDrawerContent
import com.example.ui.components.PinLockModal
import com.example.ui.components.PinModalMode
import com.example.ui.components.ThemeSelectorDialog
import com.example.ui.screens.ExportFormat
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.NoteListScreen
import com.example.ui.screens.NoteReaderScreen
import com.example.ui.theme.CloudNoteTheme
import com.example.ui.theme.LocalAppThemeMode
import com.example.util.NoteExporter
import com.example.viewmodel.CloudNoteViewModel
import com.example.viewmodel.DrawerFilter
import com.example.viewmodel.ScreenMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: CloudNoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            CloudNoteTheme(themeMode = themeMode) {
                CloudNoteApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun CloudNoteApp(viewModel: CloudNoteViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val readerFont by viewModel.readerFont.collectAsStateWithLifecycle()
    val readerFontSizeSp by viewModel.readerFontSizeSp.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val allNotes by viewModel.allUserNotes.collectAsStateWithLifecycle()
    val filteredNotes by viewModel.filteredNotes.collectAsStateWithLifecycle()
    val drawerFilter by viewModel.drawerFilter.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val screenMode by viewModel.screenMode.collectAsStateWithLifecycle()
    val activeNote by viewModel.activeNote.collectAsStateWithLifecycle()
    val unlockedNoteIds by viewModel.unlockedNoteIds.collectAsStateWithLifecycle()
    val statusToast by viewModel.statusToast.collectAsStateWithLifecycle()

    var showThemeModal by remember { mutableStateOf(false) }
    var pendingPinUnlockNote by remember { mutableStateOf<NoteModel?>(null) }
    var showPinConfigureModal by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<ExportFormat?>(null) }

    // Visibility / Resume Auto-Sync listener (matches document.addEventListener('visibilitychange'))
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Document launchers for 4-format Exports & JSON Backup/Restore
    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val note = activeNote
        val fmt = pendingExportFormat
        if (uri != null && note != null && fmt != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        when (fmt) {
                            ExportFormat.PDF -> {
                                NoteExporter.writePdfToStream(context, note, out)
                            }
                            ExportFormat.WORD -> {
                                out.write(NoteExporter.toWordDocHtml(note).toByteArray(Charsets.UTF_8))
                            }
                            ExportFormat.MARKDOWN -> {
                                out.write(NoteExporter.toMarkdown(note).toByteArray(Charsets.UTF_8))
                            }
                            ExportFormat.TXT -> {
                                out.write(NoteExporter.toPlainText(note).toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                }
                viewModel.showToast("${fmt.label} এক্সপোর্ট সম্পন্ন হয়েছে")
            }
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.createBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
                viewModel.showToast("ব্যাকআপ (.json) সেভ হয়েছে")
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    }
                }
                if (!json.isNullOrBlank()) {
                    viewModel.restoreBackupJson(json)
                }
            }
        }
    }

    val activeTheme = LocalAppThemeMode.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(activeTheme.bg)
    ) {
        if (session == null) {
            LoginScreen(
                isOnline = isOnline,
                onLoginOrRegister = { email, password, displayName, isRegister, onError ->
                    viewModel.loginOrRegister(email, password, displayName, isRegister, onError)
                },
                onGoogleSignIn = { email, displayName, onError ->
                    viewModel.loginWithGoogleAccount(email, displayName, onError)
                },
                onOpenThemeSelector = { showThemeModal = true }
            )
        } else {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = screenMode == ScreenMode.NOTE_LIST,
                drawerContent = {
                    NavigationDrawerContent(
                        session = session!!,
                        isOnline = isOnline,
                        allNotes = allNotes,
                        folders = folders,
                        currentFilter = drawerFilter,
                        selectedFolder = selectedFolder,
                        onSelectFilter = { filter, folderName ->
                            viewModel.setDrawerFilter(filter, folderName)
                            scope.launch { drawerState.close() }
                        },
                        onCreateFolder = { name ->
                            viewModel.createFolder(name)
                        },
                        onDeleteFolder = { name ->
                            viewModel.deleteFolder(name)
                        },
                        onExportBackupJson = {
                            scope.launch { drawerState.close() }
                            backupExportLauncher.launch("cloudnote_backup.json")
                        },
                        onImportBackupJson = {
                            scope.launch { drawerState.close() }
                            backupImportLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        onLogout = {
                            scope.launch { drawerState.close() }
                            viewModel.logout()
                        }
                    )
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    when (screenMode) {
                        ScreenMode.NOTE_LIST -> {
                            NoteListScreen(
                                notes = filteredNotes,
                                isOnline = isOnline,
                                isSyncing = isSyncing,
                                drawerFilter = drawerFilter,
                                selectedFolder = selectedFolder,
                                searchQuery = searchQuery,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onOpenThemeSelector = { showThemeModal = true },
                                onCreateNewNote = { viewModel.openNoteEditor(null) },
                                onOpenNote = { note ->
                                    val isPinLocked = !note.pinHash.isNullOrBlank() && !unlockedNoteIds.contains(note.id)
                                    if (isPinLocked) {
                                        pendingPinUnlockNote = note
                                    } else {
                                        viewModel.openNoteReader(note)
                                    }
                                },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onMoveToTrash = viewModel::moveToTrash,
                                onRestoreFromTrash = viewModel::restoreFromTrash,
                                onDeleteForever = viewModel::deleteForever
                            )
                        }

                        ScreenMode.READER -> {
                            val currentNote = activeNote
                            if (currentNote == null) {
                                viewModel.navigateBackToList()
                            } else {
                                NoteReaderScreen(
                                    note = currentNote,
                                    readerFont = readerFont,
                                    readerFontSizeSp = readerFontSizeSp,
                                    onSelectReaderFont = viewModel::selectReaderFont,
                                    onChangeReaderFontSize = viewModel::updateReaderFontSize,
                                    onBack = viewModel::navigateBackToList,
                                    onEditNote = { viewModel.openNoteEditor(currentNote) },
                                    onOpenThemeSelector = { showThemeModal = true },
                                    onOpenPinSettings = { showPinConfigureModal = true },
                                    onReplaceInNote = { updatedSections ->
                                        viewModel.saveNote(
                                            noteId = currentNote.id,
                                            title = currentNote.title,
                                            folder = currentNote.folder,
                                            sections = updatedSections,
                                            isFavorite = currentNote.isFavorite,
                                            isTrashed = currentNote.isTrashed,
                                            pinHash = currentNote.pinHash
                                        )
                                    },
                                    onExportFormat = { fmt ->
                                        pendingExportFormat = fmt
                                        val safeName = currentNote.title.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").ifBlank { "note" }
                                        exportDocumentLauncher.launch("$safeName.${fmt.ext}")
                                    },
                                    onLoadVersionHistory = { noteId ->
                                        viewModel.loadVersionHistory(noteId)
                                    },
                                    onRestoreVersionSnapshot = { snap ->
                                        viewModel.saveNote(
                                            noteId = currentNote.id,
                                            title = snap.title,
                                            folder = currentNote.folder,
                                            sections = snap.sections,
                                            isFavorite = currentNote.isFavorite,
                                            isTrashed = currentNote.isTrashed,
                                            pinHash = currentNote.pinHash
                                        )
                                    }
                                )
                            }
                        }

                        ScreenMode.EDITOR -> {
                            NoteEditorScreen(
                                existingNote = activeNote,
                                initialFolder = if (drawerFilter == DrawerFilter.FOLDER) selectedFolder else "সাধারণ",
                                availableFolders = folders,
                                onBack = {
                                    if (activeNote != null) {
                                        viewModel.navigateReaderFromEditor(activeNote!!.id)
                                    } else {
                                        viewModel.navigateBackToList()
                                    }
                                },
                                onOpenThemeSelector = { showThemeModal = true },
                                onCreateNewFolder = { folderName, onCreated ->
                                    viewModel.createFolder(folderName, onCreated)
                                },
                                onScheduleDraft = viewModel::scheduleDebouncedDraft,
                                onLoadDraft = viewModel::loadDraftForNote,
                                onDiscardDraft = viewModel::discardDraft,
                                onRunAiAssistant = viewModel::runAiOnSection,
                                onSaveNote = { noteId, title, folder, sections, isFav, isTrash, pin ->
                                    viewModel.saveNote(
                                        noteId = noteId,
                                        title = title,
                                        folder = folder,
                                        sections = sections,
                                        isFavorite = isFav,
                                        isTrashed = isTrash,
                                        pinHash = pin,
                                        onSaved = { saved ->
                                            viewModel.navigateReaderFromEditor(saved.id)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Subtle Status Toast Banner
        AnimatedVisibility(
            visible = statusToast != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp, start = 20.dp, end = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(activeTheme.accent)
                    .border(1.dp, activeTheme.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = statusToast ?: "",
                    color = activeTheme.accentFg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // Global 7-Mode Theme Selector Modal
    if (showThemeModal) {
        ThemeSelectorDialog(
            currentTheme = themeMode,
            onSelectTheme = viewModel::selectTheme,
            onDismiss = { showThemeModal = false }
        )
    }

    // PIN Unlock Modal when opening a locked note
    if (pendingPinUnlockNote != null) {
        val target = pendingPinUnlockNote!!
        PinLockModal(
            mode = PinModalMode.VERIFY_TO_OPEN,
            existingPinHash = target.pinHash,
            noteTitle = target.title,
            onPinVerified = {
                viewModel.markNoteUnlocked(target.id)
                pendingPinUnlockNote = null
                viewModel.openNoteReader(target)
            },
            onPinUpdated = {},
            onDismiss = { pendingPinUnlockNote = null }
        )
    }

    // PIN Set / Remove Modal inside Reader
    if (showPinConfigureModal && activeNote != null) {
        val current = activeNote!!
        PinLockModal(
            mode = PinModalMode.SET_OR_CHANGE,
            existingPinHash = current.pinHash,
            noteTitle = current.title,
            onPinVerified = {},
            onPinUpdated = { newPin ->
                viewModel.setOrRemovePin(current.id, newPin)
                showPinConfigureModal = false
            },
            onDismiss = { showPinConfigureModal = false }
        )
    }
}
