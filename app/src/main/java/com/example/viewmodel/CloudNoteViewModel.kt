package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudNoteRepository
import com.example.data.NoteDraftEntity
import com.example.data.NoteModel
import com.example.data.NoteSection
import com.example.data.NoteVersionSnapshot
import com.example.data.UserSession
import com.example.network.AiActionType
import com.example.network.GeminiAiService
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ReaderFontOption
import com.example.util.NetworkMonitor
import com.example.util.StorageChunking
import com.example.util.TextNormalizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DrawerFilter {
    ALL_NOTES,
    FAVORITES,
    TRASH,
    FOLDER
}

enum class ScreenMode {
    NOTE_LIST,
    READER,
    EDITOR
}

class CloudNoteViewModel(application: Application) : AndroidViewModel(application) {

    val repository = CloudNoteRepository(application)
    private val networkMonitor = NetworkMonitor(application)

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, networkMonitor.isCurrentlyOnline())

    val session: StateFlow<UserSession?> = repository.sessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val themeMode: StateFlow<AppThemeMode> = repository.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.LIGHT)

    val readerFont: StateFlow<ReaderFontOption> = repository.readerFontFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderFontOption.NOTO_SANS)

    val readerFontSizeSp: StateFlow<Int> = repository.readerFontSizeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 18)

    val folders: StateFlow<List<String>> = repository.observeAllFolders()
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.DEFAULT_FOLDERS)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allUserNotes: StateFlow<List<NoteModel>> = session.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            repository.observeHydratedNotes(user.userId)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _drawerFilter = MutableStateFlow(DrawerFilter.ALL_NOTES)
    val drawerFilter: StateFlow<DrawerFilter> = _drawerFilter.asStateFlow()

    private val _selectedFolder = MutableStateFlow("সাধারণ")
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _screenMode = MutableStateFlow(ScreenMode.NOTE_LIST)
    val screenMode: StateFlow<ScreenMode> = _screenMode.asStateFlow()

    private val _activeNoteId = MutableStateFlow<String?>(null)
    val activeNoteId: StateFlow<String?> = _activeNoteId.asStateFlow()

    // Unlocks for PIN-protected notes during current session
    private val _unlockedNoteIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedNoteIds: StateFlow<Set<String>> = _unlockedNoteIds.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var draftDebounceJob: Job? = null

    val filteredNotes: StateFlow<List<NoteModel>> = combine(
        allUserNotes,
        _drawerFilter,
        _selectedFolder,
        _searchQuery
    ) { notes, filter, folder, query ->
        val base = when (filter) {
            DrawerFilter.ALL_NOTES -> notes.filter { !it.isTrashed }
            DrawerFilter.FAVORITES -> notes.filter { !it.isTrashed && it.isFavorite }
            DrawerFilter.TRASH -> notes.filter { it.isTrashed }
            DrawerFilter.FOLDER -> notes.filter { !it.isTrashed && it.folder == folder }
        }
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) {
            base
        } else {
            base.filter { note ->
                note.title.lowercase().contains(cleanQuery) ||
                    note.folder.lowercase().contains(cleanQuery) ||
                    note.sections.any { sec ->
                        sec.title.lowercase().contains(cleanQuery) ||
                            sec.content.lowercase().contains(cleanQuery)
                    }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeNote: StateFlow<NoteModel?> = combine(allUserNotes, _activeNoteId) { notes, id ->
        if (id == null) null else notes.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Automatic Background Cloud Sync When Online (Step 3)
        viewModelScope.launch {
            combine(isOnline, session, allUserNotes) { online, user, notes ->
                Triple(online, user, notes.any { it.needsCloudSync })
            }.collect { (online, user, hasUnsynced) ->
                if (online && user != null && hasUnsynced && !_isSyncing.value) {
                    triggerAutoCloudSync(user.userId)
                }
            }
        }
    }

    fun onAppResumed() {
        val user = session.value ?: return
        if (networkMonitor.isCurrentlyOnline()) {
            triggerAutoCloudSync(user.userId)
        }
    }

    private fun triggerAutoCloudSync(userId: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                repository.syncPendingNotesToCloud(userId)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun showToast(message: String) {
        _statusToast.value = message
        viewModelScope.launch {
            delay(2800)
            if (_statusToast.value == message) {
                _statusToast.value = null
            }
        }
    }

    // --- Auth ---
    fun loginOrRegister(
        email: String,
        password: String,
        displayName: String?,
        isRegister: Boolean,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.loginOrRegisterWithEmail(
                email = email,
                password = password,
                displayName = displayName,
                isRegister = isRegister,
                isOnline = isOnline.value
            )
            result.onFailure { onError(it.message ?: "লগইন ব্যর্থ হয়েছে") }
        }
    }

    fun loginWithGoogleAccount(email: String, displayName: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.loginWithGoogleVault(
                email = email,
                displayName = displayName,
                isOnline = isOnline.value
            )
            result.onFailure { onError(it.message ?: "গুগল লগইন ব্যর্থ হয়েছে") }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _activeNoteId.value = null
            _screenMode.value = ScreenMode.NOTE_LIST
            _unlockedNoteIds.value = emptySet()
        }
    }

    // --- Theme & Reader Settings ---
    fun selectTheme(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun selectReaderFont(font: ReaderFontOption) {
        viewModelScope.launch {
            repository.setReaderFont(font)
        }
    }

    fun updateReaderFontSize(sizeSp: Int) {
        viewModelScope.launch {
            repository.setReaderFontSize(sizeSp)
        }
    }

    // --- Navigation & Filtering ---
    fun setDrawerFilter(filter: DrawerFilter, folderName: String? = null) {
        _drawerFilter.value = filter
        if (folderName != null) {
            _selectedFolder.value = folderName
        }
        _screenMode.value = ScreenMode.NOTE_LIST
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openNoteReader(note: NoteModel) {
        _activeNoteId.value = note.id
        _screenMode.value = ScreenMode.READER
    }

    fun openNoteEditor(note: NoteModel?) {
        _activeNoteId.value = note?.id
        _screenMode.value = ScreenMode.EDITOR
    }

    fun navigateBackToList() {
        _screenMode.value = ScreenMode.NOTE_LIST
        _activeNoteId.value = null
    }

    fun navigateReaderFromEditor(savedNoteId: String) {
        _activeNoteId.value = savedNoteId
        _screenMode.value = ScreenMode.READER
    }

    // --- Folders ---
    fun createFolder(name: String, onCreated: ((String) -> Unit)? = null) {
        val cleaned = TextNormalizer.normalizeAndClean(name.trim())
        if (cleaned.isBlank()) return
        viewModelScope.launch {
            val ok = repository.createFolder(cleaned, isOnline.value)
            if (ok) {
                showToast("ফোল্ডার তৈরি হয়েছে: $cleaned")
                onCreated?.invoke(cleaned)
            }
        }
    }

    fun deleteFolder(name: String) {
        viewModelScope.launch {
            val deleted = repository.deleteCustomFolder(name)
            if (deleted) {
                if (_selectedFolder.value == name) {
                    _selectedFolder.value = "সাধারণ"
                    _drawerFilter.value = DrawerFilter.ALL_NOTES
                }
                showToast("ফোল্ডার মুছে ফেলা হয়েছে")
            }
        }
    }

    // --- Note Operations ---
    fun saveNote(
        noteId: String?,
        title: String,
        folder: String,
        sections: List<NoteSection>,
        isFavorite: Boolean,
        isTrashed: Boolean,
        pinHash: String?,
        onSaved: (NoteModel) -> Unit = {}
    ) {
        val user = session.value ?: return
        viewModelScope.launch {
            val saved = repository.saveNote(
                userId = user.userId,
                noteId = noteId,
                title = title,
                folder = folder,
                sections = sections,
                isFavorite = isFavorite,
                isTrashed = isTrashed,
                pinHash = pinHash,
                isOnline = isOnline.value
            )
            _activeNoteId.value = saved.id
            showToast(if (isOnline.value) "নোট সেভ ও ক্লাউডে সিঙ্ক হয়েছে" else "অফলাইনে ফোনে সেভ হয়েছে")
            onSaved(saved)
        }
    }

    fun scheduleDebouncedDraft(noteId: String, title: String, folder: String, sections: List<NoteSection>) {
        draftDebounceJob?.cancel()
        draftDebounceJob = viewModelScope.launch {
            delay(1000L) // 1-second debounced local draft save
            repository.saveDraft(noteId, title, folder, sections)
        }
    }

    suspend fun loadDraftForNote(noteId: String): NoteDraftEntity? {
        return repository.getDraft(noteId)
    }

    fun discardDraft(noteId: String) {
        viewModelScope.launch {
            repository.clearDraft(noteId)
        }
    }

    fun toggleFavorite(noteId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(noteId, isOnline.value)
        }
    }

    fun moveToTrash(noteId: String) {
        viewModelScope.launch {
            repository.setTrashStatus(noteId, trashed = true, isOnline = isOnline.value)
            if (_activeNoteId.value == noteId) {
                navigateBackToList()
            }
            showToast("ট্র্যাশে সরানো হয়েছে")
        }
    }

    fun restoreFromTrash(noteId: String) {
        viewModelScope.launch {
            repository.setTrashStatus(noteId, trashed = false, isOnline = isOnline.value)
            showToast("নোট পুনরুদ্ধার করা হয়েছে")
        }
    }

    fun deleteForever(noteId: String) {
        viewModelScope.launch {
            repository.deleteNotePermanently(noteId)
            if (_activeNoteId.value == noteId) {
                navigateBackToList()
            }
            showToast("স্থায়ীভাবে মুছে ফেলা হয়েছে")
        }
    }

    // --- PIN Lock ---
    fun markNoteUnlocked(noteId: String) {
        _unlockedNoteIds.value = _unlockedNoteIds.value + noteId
    }

    fun setOrRemovePin(noteId: String, rawPin: String?) {
        viewModelScope.launch {
            val normalized = rawPin?.let { TextNormalizer.normalizePinDigits(it) }?.takeIf { it.length == 4 }
            repository.updateNotePin(noteId, normalized, isOnline.value)
            if (normalized != null) {
                _unlockedNoteIds.value = _unlockedNoteIds.value + noteId
                showToast("৪-ডিজিট পিন লক সক্রিয় হয়েছে")
            } else {
                showToast("পিন লক সরানো হয়েছে")
            }
        }
    }

    // --- Version History ---
    suspend fun loadVersionHistory(noteId: String): List<NoteVersionSnapshot> {
        return repository.getVersionHistory(noteId)
    }

    // --- Gemini AI Per-Section Assistant ---
    fun runAiOnSection(
        action: AiActionType,
        inputText: String,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val res = GeminiAiService.processSectionText(action, inputText)
            onResult(res)
        }
    }

    // --- Backup & Restore ---
    suspend fun createBackupJson(): String {
        val uid = session.value?.userId ?: return "{}"
        return repository.exportFullBackupJson(uid)
    }

    fun restoreBackupJson(jsonString: String) {
        val uid = session.value?.userId ?: return
        viewModelScope.launch {
            val result = repository.importFullBackupJson(uid, jsonString, isOnline.value)
            result.onSuccess { count ->
                showToast("${TextNormalizer.toBengaliDigits(count)}টি নোট সফলভাবে রিস্টোর হয়েছে")
            }.onFailure {
                showToast("ব্যাকআপ রিস্টোর ব্যর্থ হয়েছে")
            }
        }
    }
}
