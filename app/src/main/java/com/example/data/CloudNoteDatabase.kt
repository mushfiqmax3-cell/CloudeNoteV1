package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudNoteDao {

    // --- Notes ---
    @Query("SELECT * FROM notes WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeNotesForUser(userId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE userId = :userId ORDER BY updatedAt DESC")
    suspend fun getNotesForUserOnce(userId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteById(noteId: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE userId = :userId AND needsCloudSync = 1")
    suspend fun getUnsyncedNotes(userId: String): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: String)

    // --- Note Chunks ---
    @Query("SELECT * FROM note_chunks WHERE noteId = :noteId ORDER BY chunkIndex ASC")
    suspend fun getChunksForNote(noteId: String): List<NoteChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<NoteChunkEntity>)

    @Query("DELETE FROM note_chunks WHERE noteId = :noteId")
    suspend fun deleteChunksForNote(noteId: String)

    // --- Drafts ---
    @Query("SELECT * FROM drafts WHERE noteId = :noteId LIMIT 1")
    suspend fun getDraft(noteId: String): NoteDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: NoteDraftEntity)

    @Query("DELETE FROM drafts WHERE noteId = :noteId")
    suspend fun deleteDraft(noteId: String)

    // --- Version Snapshots (Max 25 per note) ---
    @Query("SELECT * FROM version_snapshots WHERE noteId = :noteId ORDER BY timestamp DESC")
    suspend fun getSnapshotsForNote(noteId: String): List<VersionSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: VersionSnapshotEntity)

    @Query("DELETE FROM version_snapshots WHERE id IN (SELECT id FROM version_snapshots WHERE noteId = :noteId ORDER BY timestamp DESC LIMIT -1 OFFSET 25)")
    suspend fun pruneSnapshots(noteId: String)

    @Query("DELETE FROM version_snapshots WHERE noteId = :noteId")
    suspend fun deleteSnapshotsForNote(noteId: String)

    // --- Custom Folders ---
    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    fun observeCustomFolders(): Flow<List<CustomFolderEntity>>

    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    suspend fun getCustomFoldersOnce(): List<CustomFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomFolder(folder: CustomFolderEntity)

    @Query("DELETE FROM folders WHERE name = :name")
    suspend fun deleteCustomFolder(name: String)

    @Query("UPDATE notes SET folder = 'সাধারণ', needsCloudSync = 1 WHERE folder = :deletedFolder")
    suspend fun reassignNotesFromFolder(deletedFolder: String)

    // --- Cloud Vault Mirror (Simulates /users/{userId}/notes/{noteId} & /note_chunks/chunk_{i} writeBatch) ---
    @Query("SELECT * FROM cloud_vault_notes WHERE userId = :userId AND noteId = :noteId LIMIT 1")
    suspend fun getCloudVaultNote(userId: String, noteId: String): CloudVaultNoteEntity?

    @Query("SELECT * FROM cloud_vault_notes WHERE userId = :userId")
    suspend fun getAllCloudVaultNotes(userId: String): List<CloudVaultNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCloudVaultNote(cloudNote: CloudVaultNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCloudVaultChunks(chunks: List<CloudVaultChunkEntity>)

    @Query("DELETE FROM cloud_vault_chunks WHERE userId = :userId AND noteId = :noteId")
    suspend fun deleteCloudVaultChunks(userId: String, noteId: String)

    // --- Account Vault ---
    @Query("SELECT * FROM account_vault WHERE email = :email LIMIT 1")
    suspend fun getAccountByEmail(email: String): AccountVaultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: AccountVaultEntity)
}

@Database(
    entities = [
        NoteEntity::class,
        NoteChunkEntity::class,
        NoteDraftEntity::class,
        VersionSnapshotEntity::class,
        CustomFolderEntity::class,
        CloudVaultNoteEntity::class,
        CloudVaultChunkEntity::class,
        AccountVaultEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CloudNoteDatabase : RoomDatabase() {
    abstract fun dao(): CloudNoteDao

    companion object {
        @Volatile
        private var INSTANCE: CloudNoteDatabase? = null

        fun getInstance(context: Context): CloudNoteDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CloudNoteDatabase::class.java,
                    "cloudnote_studio.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
