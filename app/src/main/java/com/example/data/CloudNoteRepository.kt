package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ReaderFontOption
import com.example.util.StorageChunking
import com.example.util.TextNormalizer
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "cloudnote_prefs")

@JsonClass(generateAdapter = true)
data class FullBackupPayload(
    val version: Int = 1,
    val exportedAt: String,
    val customFolders: List<String>,
    val notes: List<BackupNoteItem>
)

@JsonClass(generateAdapter = true)
data class BackupNoteItem(
    val id: String,
    val title: String,
    val folder: String,
    val sections: List<NoteSection>,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val pinHash: String?,
    val createdAt: String,
    val updatedAt: String
)

class CloudNoteRepository(private val context: Context) {

    private val db = CloudNoteDatabase.getInstance(context)
    private val dao = db.dao()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val backupAdapter = moshi.adapter(FullBackupPayload::class.java)

    companion object {
        private val KEY_USER_ID = stringPreferencesKey("session_user_id")
        private val KEY_USER_EMAIL = stringPreferencesKey("session_user_email")
        private val KEY_USER_NAME = stringPreferencesKey("session_user_name")
        private val KEY_USER_PROVIDER = stringPreferencesKey("session_user_provider")

        private val KEY_THEME_MODE = stringPreferencesKey("app_theme_mode")
        private val KEY_READER_FONT = stringPreferencesKey("reader_font_family")
        private val KEY_READER_FONT_SIZE = intPreferencesKey("reader_font_size_sp")
    }

    // --- User Session & Preferences ---
    val sessionFlow: Flow<UserSession?> = context.dataStore.data.map { prefs ->
        val uid = prefs[KEY_USER_ID]
        val email = prefs[KEY_USER_EMAIL]
        val name = prefs[KEY_USER_NAME]
        val provider = prefs[KEY_USER_PROVIDER] ?: "email"
        if (!uid.isNullOrBlank() && !email.isNullOrBlank()) {
            UserSession(
                userId = uid,
                email = email,
                displayName = name ?: email.substringBefore("@"),
                provider = provider
            )
        } else {
            null
        }
    }

    val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        AppThemeMode.fromId(prefs[KEY_THEME_MODE] ?: AppThemeMode.LIGHT.id)
    }

    val readerFontFlow: Flow<ReaderFontOption> = context.dataStore.data.map { prefs ->
        ReaderFontOption.fromId(prefs[KEY_READER_FONT] ?: ReaderFontOption.NOTO_SANS.id)
    }

    val readerFontSizeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_READER_FONT_SIZE] ?: 18).coerceIn(14, 28)
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.id }
    }

    suspend fun setReaderFont(font: ReaderFontOption) {
        context.dataStore.edit { it[KEY_READER_FONT] = font.id }
    }

    suspend fun setReaderFontSize(sizeSp: Int) {
        context.dataStore.edit { it[KEY_READER_FONT_SIZE] = sizeSp.coerceIn(14, 28) }
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // --- Step 1: Mandatory Online First-Time Login & Account Vault ---
    suspend fun loginOrRegisterWithEmail(
        email: String,
        password: String,
        displayName: String?,
        isRegister: Boolean,
        isOnline: Boolean
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        if (!isOnline) {
            return@withContext Result.failure(
                IllegalStateException("প্রথমবার লগইনের জন্য ইন্টারনেট সংযোগ চালু করুন")
            )
        }
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return@withContext Result.failure(IllegalArgumentException("সঠিক ইমেইল ঠিকানা লিখুন"))
        }
        if (password.length < 4) {
            return@withContext Result.failure(IllegalArgumentException("পাসওয়ার্ড অন্তত ৪ অক্ষরের হতে হবে"))
        }

        val passHash = hashString(password)
        val existing = dao.getAccountByEmail(cleanEmail)

        val account = if (existing != null) {
            if (isRegister) {
                return@withContext Result.failure(IllegalArgumentException("এই ইমেইল দিয়ে ইতিমধ্যে অ্যাকাউন্ট রয়েছে, লগইন করুন"))
            }
            if (existing.passwordHash != passHash && existing.provider == "email") {
                return@withContext Result.failure(IllegalArgumentException("ভুল পাসওয়ার্ড দিয়েছেন"))
            }
            existing
        } else {
            val newAcc = AccountVaultEntity(
                email = cleanEmail,
                userId = "usr_" + hashString(cleanEmail).take(16),
                displayName = displayName?.takeIf { it.isNotBlank() } ?: cleanEmail.substringBefore("@"),
                passwordHash = passHash,
                provider = "email",
                createdAt = Instant.now().toString()
            )
            dao.upsertAccount(newAcc)
            newAcc
        }

        val session = UserSession(
            userId = account.userId,
            email = account.email,
            displayName = account.displayName,
            provider = account.provider
        )
        persistSession(session)
        seedStarterNotesIfEmpty(session.userId, isOnline = true)
        Result.success(session)
    }

    suspend fun loginWithGoogleVault(
        email: String,
        displayName: String,
        isOnline: Boolean
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        if (!isOnline) {
            return@withContext Result.failure(
                IllegalStateException("প্রথমবার লগইনের জন্য ইন্টারনেট সংযোগ চালু করুন")
            )
        }
        val cleanEmail = email.trim().lowercase().ifBlank { "user@gmail.com" }
        val cleanName = displayName.trim().ifBlank { cleanEmail.substringBefore("@") }
        val existing = dao.getAccountByEmail(cleanEmail)
        val account = existing ?: AccountVaultEntity(
            email = cleanEmail,
            userId = "usr_" + hashString(cleanEmail).take(16),
            displayName = cleanName,
            passwordHash = hashString("google_oauth_$cleanEmail"),
            provider = "google",
            createdAt = Instant.now().toString()
        ).also { dao.upsertAccount(it) }

        val session = UserSession(
            userId = account.userId,
            email = account.email,
            displayName = account.displayName,
            provider = "google"
        )
        persistSession(session)
        seedStarterNotesIfEmpty(session.userId, isOnline = true)
        Result.success(session)
    }

    private suspend fun persistSession(session: UserSession) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = session.userId
            prefs[KEY_USER_EMAIL] = session.email
            prefs[KEY_USER_NAME] = session.displayName
            prefs[KEY_USER_PROVIDER] = session.provider
        }
    }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_USER_PROVIDER)
        }
    }

    // --- Folders ---
    fun observeAllFolders(): Flow<List<String>> = dao.observeCustomFolders().map { customList ->
        val merged = LinkedHashSet<String>()
        merged.addAll(DEFAULT_FOLDERS)
        customList.forEach { merged.add(it.name) }
        merged.toList()
    }

    suspend fun createFolder(folderName: String, isOnline: Boolean): Boolean = withContext(Dispatchers.IO) {
        val cleaned = TextNormalizer.normalizeAndClean(folderName.trim())
        if (cleaned.isBlank()) return@withContext false
        if (DEFAULT_FOLDERS.contains(cleaned)) return@withContext true
        dao.upsertCustomFolder(
            CustomFolderEntity(
                name = cleaned,
                createdAt = Instant.now().toString(),
                needsCloudSync = !isOnline
            )
        )
        true
    }

    suspend fun deleteCustomFolder(folderName: String): Boolean = withContext(Dispatchers.IO) {
        if (folderName == "সাধারণ" || DEFAULT_FOLDERS.contains(folderName)) return@withContext false
        dao.deleteCustomFolder(folderName)
        dao.reassignNotesFromFolder(folderName)
        true
    }

    // --- Notes Hydration & Observation ---
    fun observeHydratedNotes(userId: String): Flow<List<NoteModel>> {
        return dao.observeNotesForUser(userId).map { entities ->
            entities.map { entity -> hydrateEntity(entity) }
        }
    }

    private suspend fun hydrateEntity(entity: NoteEntity): NoteModel {
        val chunks = if (entity.isChunked) {
            dao.getChunksForNote(entity.id)
        } else {
            emptyList()
        }
        val sections = StorageChunking.hydrateNoteInMemory(
            isChunked = entity.isChunked,
            inlineJson = entity.sectionsJson,
            chunks = chunks
        )
        return NoteModel(
            id = entity.id,
            userId = entity.userId,
            title = entity.title,
            folder = entity.folder.ifBlank { "সাধারণ" },
            sections = sections,
            isChunked = entity.isChunked,
            chunkCount = entity.chunkCount,
            isFavorite = entity.isFavorite,
            isTrashed = entity.isTrashed,
            pinHash = entity.pinHash,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            needsCloudSync = entity.needsCloudSync,
            syncUpdatedAt = entity.syncUpdatedAt
        )
    }

    suspend fun saveNote(
        userId: String,
        noteId: String?,
        title: String,
        folder: String,
        sections: List<NoteSection>,
        isFavorite: Boolean,
        isTrashed: Boolean,
        pinHash: String?,
        isOnline: Boolean,
        recordSnapshot: Boolean = true
    ): NoteModel = withContext(Dispatchers.IO) {
        val id = noteId ?: ("note_" + UUID.randomUUID().toString().replace("-", "").take(12))
        val existing = dao.getNoteById(id)
        val nowIso = Instant.now().toString()

        val normalizedTitle = TextNormalizer.normalizeAndClean(title.trim()).ifBlank { "শিরোনামহীন নোট" }
        val normalizedFolder = TextNormalizer.normalizeAndClean(folder.trim()).ifBlank { "সাধারণ" }
        val normalizedSections = sections.ifEmpty {
            listOf(NoteSection(id = "sec_1", title = "", content = ""))
        }.map { sec ->
            sec.copy(
                title = TextNormalizer.normalizeAndClean(sec.title),
                content = TextNormalizer.normalizeAndClean(sec.content)
            )
        }

        // Auto-chunking check (> 550KB splits into 150,000-char chunks)
        val chunkResult = StorageChunking.prepareNoteStorage(id, normalizedSections)
        dao.deleteChunksForNote(id)
        if (chunkResult.isChunked && chunkResult.chunks.isNotEmpty()) {
            dao.insertChunks(chunkResult.chunks)
        }

        val entity = NoteEntity(
            id = id,
            userId = userId,
            title = normalizedTitle,
            folder = normalizedFolder,
            sectionsJson = chunkResult.inlineJson,
            isChunked = chunkResult.isChunked,
            chunkCount = chunkResult.chunks.size,
            isFavorite = isFavorite,
            isTrashed = isTrashed,
            pinHash = pinHash,
            createdAt = existing?.createdAt ?: nowIso,
            updatedAt = nowIso,
            needsCloudSync = true, // Marked dirty first, then auto-synced immediately if online
            syncUpdatedAt = nowIso
        )

        dao.upsertNote(entity)
        dao.deleteDraft(id)

        // Save 25-snapshot local version history
        if (recordSnapshot) {
            val snap = VersionSnapshotEntity(
                id = "snap_" + UUID.randomUUID().toString().take(10),
                noteId = id,
                timestamp = nowIso,
                title = normalizedTitle,
                sectionsJson = StorageChunking.serializeSections(normalizedSections)
            )
            dao.insertSnapshot(snap)
            dao.pruneSnapshots(id)
        }

        if (isOnline) {
            syncSingleNoteToCloud(entity, chunkResult.chunks)
        }

        val finalEntity = dao.getNoteById(id) ?: entity
        hydrateEntity(finalEntity)
    }

    suspend fun toggleFavorite(noteId: String, isOnline: Boolean) = withContext(Dispatchers.IO) {
        val existing = dao.getNoteById(noteId) ?: return@withContext
        val nowIso = Instant.now().toString()
        val updated = existing.copy(
            isFavorite = !existing.isFavorite,
            updatedAt = nowIso,
            needsCloudSync = true,
            syncUpdatedAt = nowIso
        )
        dao.upsertNote(updated)
        if (isOnline) {
            val chunks = if (updated.isChunked) dao.getChunksForNote(noteId) else emptyList()
            syncSingleNoteToCloud(updated, chunks)
        }
    }

    suspend fun setTrashStatus(noteId: String, trashed: Boolean, isOnline: Boolean) = withContext(Dispatchers.IO) {
        val existing = dao.getNoteById(noteId) ?: return@withContext
        val nowIso = Instant.now().toString()
        val updated = existing.copy(
            isTrashed = trashed,
            updatedAt = nowIso,
            needsCloudSync = true,
            syncUpdatedAt = nowIso
        )
        dao.upsertNote(updated)
        if (isOnline) {
            val chunks = if (updated.isChunked) dao.getChunksForNote(noteId) else emptyList()
            syncSingleNoteToCloud(updated, chunks)
        }
    }

    suspend fun deleteNotePermanently(noteId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getNoteById(noteId)
        dao.deleteChunksForNote(noteId)
        dao.deleteSnapshotsForNote(noteId)
        dao.deleteDraft(noteId)
        dao.deleteNoteById(noteId)
        if (existing != null) {
            dao.deleteCloudVaultChunks(existing.userId, noteId)
        }
    }

    suspend fun updateNotePin(noteId: String, newPin: String?, isOnline: Boolean) = withContext(Dispatchers.IO) {
        val existing = dao.getNoteById(noteId) ?: return@withContext
        val normalizedPin = newPin?.let { TextNormalizer.normalizePinDigits(it) }?.takeIf { it.length == 4 }
        val nowIso = Instant.now().toString()
        val updated = existing.copy(
            pinHash = normalizedPin,
            updatedAt = nowIso,
            needsCloudSync = true,
            syncUpdatedAt = nowIso
        )
        dao.upsertNote(updated)
        if (isOnline) {
            val chunks = if (updated.isChunked) dao.getChunksForNote(noteId) else emptyList()
            syncSingleNoteToCloud(updated, chunks)
        }
    }

    // --- Step 3: Automatic Background Cloud Sync Engine (Last-Write-Wins) ---
    suspend fun syncPendingNotesToCloud(userId: String): Int = withContext(Dispatchers.IO) {
        val unsynced = dao.getUnsyncedNotes(userId)
        var syncedCount = 0
        for (note in unsynced) {
            val chunks = if (note.isChunked) dao.getChunksForNote(note.id) else emptyList()
            syncSingleNoteToCloud(note, chunks)
            syncedCount++
        }
        syncedCount
    }

    private suspend fun syncSingleNoteToCloud(note: NoteEntity, chunks: List<NoteChunkEntity>) {
        // Last-Write-Wins Conflict Resolution comparing updatedAt timestamps
        val cloudRecord = dao.getCloudVaultNote(note.userId, note.id)
        if (cloudRecord == null || note.updatedAt >= cloudRecord.updatedAt) {
            dao.deleteCloudVaultChunks(note.userId, note.id)
            if (note.isChunked && chunks.isNotEmpty()) {
                val cloudChunks = chunks.map { c ->
                    CloudVaultChunkEntity(
                        userId = note.userId,
                        noteId = note.id,
                        chunkIndex = c.chunkIndex,
                        content = c.content
                    )
                }
                dao.insertCloudVaultChunks(cloudChunks)
            }
            dao.upsertCloudVaultNote(
                CloudVaultNoteEntity(
                    userId = note.userId,
                    noteId = note.id,
                    payloadJson = note.sectionsJson,
                    chunkCount = note.chunkCount,
                    updatedAt = note.updatedAt
                )
            )
        }
        // Mark needsCloudSync = false locally
        dao.upsertNote(
            note.copy(
                needsCloudSync = false,
                syncUpdatedAt = Instant.now().toString()
            )
        )
    }

    // --- 1-Second Debounced Local Drafts ---
    suspend fun saveDraft(noteId: String, title: String, folder: String, sections: List<NoteSection>) = withContext(Dispatchers.IO) {
        val json = StorageChunking.serializeSections(sections)
        dao.upsertDraft(
            NoteDraftEntity(
                noteId = noteId,
                title = title,
                folder = folder,
                sectionsJson = json,
                updatedAt = Instant.now().toString()
            )
        )
    }

    suspend fun getDraft(noteId: String): NoteDraftEntity? = withContext(Dispatchers.IO) {
        dao.getDraft(noteId)
    }

    suspend fun clearDraft(noteId: String) = withContext(Dispatchers.IO) {
        dao.deleteDraft(noteId)
    }

    // --- 25-Snapshot Version History ---
    suspend fun getVersionHistory(noteId: String): List<NoteVersionSnapshot> = withContext(Dispatchers.IO) {
        dao.getSnapshotsForNote(noteId).map { entity ->
            NoteVersionSnapshot(
                id = entity.id,
                timestamp = entity.timestamp,
                title = entity.title,
                sections = StorageChunking.deserializeSections(entity.sectionsJson)
            )
        }
    }

    // --- Full JSON Backup & Restore ---
    suspend fun exportFullBackupJson(userId: String): String = withContext(Dispatchers.IO) {
        val customFolders = dao.getCustomFoldersOnce().map { it.name }
        val notes = dao.getNotesForUserOnce(userId).map { hydrateEntity(it) }
        val payload = FullBackupPayload(
            version = 1,
            exportedAt = Instant.now().toString(),
            customFolders = customFolders,
            notes = notes.map { n ->
                BackupNoteItem(
                    id = n.id,
                    title = n.title,
                    folder = n.folder,
                    sections = n.sections,
                    isFavorite = n.isFavorite,
                    isTrashed = n.isTrashed,
                    pinHash = n.pinHash,
                    createdAt = n.createdAt,
                    updatedAt = n.updatedAt
                )
            }
        )
        backupAdapter.toJson(payload)
    }

    suspend fun importFullBackupJson(userId: String, jsonString: String, isOnline: Boolean): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val payload = backupAdapter.fromJson(jsonString)
                ?: return@withContext Result.failure(IllegalArgumentException("অবৈধ ব্যাকআপ ফাইল"))
            payload.customFolders.forEach { folderName ->
                createFolder(folderName, isOnline)
            }
            var importedCount = 0
            for (item in payload.notes) {
                saveNote(
                    userId = userId,
                    noteId = item.id,
                    title = item.title,
                    folder = item.folder,
                    sections = item.sections,
                    isFavorite = item.isFavorite,
                    isTrashed = item.isTrashed,
                    pinHash = item.pinHash,
                    isOnline = isOnline,
                    recordSnapshot = true
                )
                importedCount++
            }
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Starter Bengali Notes ---
    private suspend fun seedStarterNotesIfEmpty(userId: String, isOnline: Boolean) {
        val current = dao.getNotesForUserOnce(userId)
        if (current.isNotEmpty()) return

        saveNote(
            userId = userId,
            noteId = "note_welcome_bn",
            title = "ক্লাউডনোট স্টুডিও গাইড ও বাংলা ইউনিকোড",
            folder = "সাধারণ",
            sections = listOf(
                NoteSection(
                    id = "sec_1",
                    title = "১. অফলাইন স্টোরেজ ও অটো-সিঙ্ক",
                    content = """
                        # স্বাগতম ক্লাউডনোট স্টুডিওতে
                        
                        আপনার সব নোট ফোনের অভ্যন্তরীণ স্টোরেজে তাৎক্ষণিক সেভ হয় এবং ইন্টারনেট সংযোগ পেলেই স্বয়ংক্রিয়ভাবে ক্লাউডে সিঙ্ক হয়।
                        
                        - **NFC বাংলা ইউনিকোড**: র্য এবং অর্ধ-অক্ষর (ZWNJ/ZWJ) নিখুঁতভাবে সংরক্ষিত থাকে।
                        - **৭টি থিম মোড**: লাইট, ডার্ক, আই রিল্যাক্সিং, গ্রিন, সেপিয়া, মিডনাইট এবং অ্যামোলেড ব্ল্যাক।
                        - **৪-ডিজিট পিন লক**: বাংলা (`০-৯`) এবং ইংরেজি (`0-9`) উভয় সংখ্যায় পিন কাজ করে।
                    """.trimIndent()
                ),
                NoteSection(
                    id = "sec_2",
                    title = "২. গণিত (KaTeX) ও মার্কডাউন টেবিল",
                    content = """
                        ## গাণিতিক সমীকরণ ও সারণি
                        
                        ইনলাইন সমীকরণ যেমন ${'$'}E = mc^2${'$'} এবং ব্লক সমীকরণ:
                        
                        ${'$'}${'$'}
                        \int_{0}^{\infty} e^{-x^2} dx = \frac{\sqrt{\pi}}{2}
                        ${'$'}${'$'}
                        
                        | ফিচার | স্ট্যাটাস | শর্টকাট |
                        | :--- | :--- | :--- |
                        | অটো-চ্যাঙ্কিং | ৫৫০ KB+ | স্বয়ংক্রিয় |
                        | পিডিএফ এক্সপোর্ট | সক্রিয় | ১-ক্লিক |
                        | বাংলা অডিও পাঠ | সক্রিয় | bn-BD TTS |
                    """.trimIndent()
                )
            ),
            isFavorite = true,
            isTrashed = false,
            pinHash = null,
            isOnline = isOnline
        )

        saveNote(
            userId = userId,
            noteId = "note_research_bn",
            title = "অ্যালগরিদম ও ডেটা স্ট্রাকচার নোট",
            folder = "কোডিং ও প্রজেক্ট",
            sections = listOf(
                NoteSection(
                    id = "sec_code_1",
                    title = "বাইনারি সার্চ ইমপ্লিমেন্টেশন",
                    content = """
                        ### টাইম কমপ্লেক্সিটি বিশ্লেষণ
                        
                        বাইনারি সার্চের টাইম কমপ্লেক্সিটি হলো ${'$'}O(\log n)${'$'} এবং স্পেস কমপ্লেক্সিটি ${'$'}O(1)${'$'}।
                        
                        ```kotlin
                        fun binarySearch(arr: IntArray, target: Int): Int {
                            var low = 0
                            var high = arr.lastIndex
                            while (low <= high) {
                                val mid = (low + high) ushr 1
                                when {
                                    arr[mid] < target -> low = mid + 1
                                    arr[mid] > target -> high = mid - 1
                                    else -> return mid
                                }
                            }
                            return -1
                        }
                        ```
                    """.trimIndent()
                )
            ),
            isFavorite = false,
            isTrashed = false,
            pinHash = null,
            isOnline = isOnline
        )
    }
}
