package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

val DEFAULT_FOLDERS = listOf(
    "সাধারণ",
    "ব্যক্তিগত",
    "পড়াশোনা ও গবেষণা",
    "বিজ্ঞান ও গণিত",
    "কোডিং ও প্রজেক্ট"
)

@JsonClass(generateAdapter = true)
data class NoteSection(
    val id: String,
    val title: String = "",
    val content: String = ""
)

@JsonClass(generateAdapter = true)
data class NoteVersionSnapshot(
    val id: String,
    val timestamp: String,
    val title: String,
    val sections: List<NoteSection>
)

@Entity(tableName = "notes")
@JsonClass(generateAdapter = true)
data class NoteEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val folder: String = "সাধারণ",
    val sectionsJson: String, // Inline JSON if <= 550KB, or empty when chunked
    val isChunked: Boolean = false,
    val chunkCount: Int = 0,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val pinHash: String? = null, // 4-digit normalized PIN if locked
    val createdAt: String,
    val updatedAt: String,
    val needsCloudSync: Boolean = true,
    val syncUpdatedAt: String = updatedAt
)

@Entity(tableName = "note_chunks", primaryKeys = ["noteId", "chunkIndex"])
@JsonClass(generateAdapter = true)
data class NoteChunkEntity(
    val noteId: String,
    val chunkIndex: Int,
    val content: String
)

@Entity(tableName = "drafts")
@JsonClass(generateAdapter = true)
data class NoteDraftEntity(
    @PrimaryKey val noteId: String,
    val title: String,
    val folder: String,
    val sectionsJson: String,
    val updatedAt: String
)

@Entity(tableName = "version_snapshots")
@JsonClass(generateAdapter = true)
data class VersionSnapshotEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val timestamp: String,
    val title: String,
    val sectionsJson: String
)

@Entity(tableName = "folders")
@JsonClass(generateAdapter = true)
data class CustomFolderEntity(
    @PrimaryKey val name: String,
    val createdAt: String,
    val needsCloudSync: Boolean = true
)

@Entity(tableName = "cloud_vault_notes", primaryKeys = ["userId", "noteId"])
@JsonClass(generateAdapter = true)
data class CloudVaultNoteEntity(
    val userId: String,
    val noteId: String,
    val payloadJson: String,
    val chunkCount: Int,
    val updatedAt: String
)

@Entity(tableName = "cloud_vault_chunks", primaryKeys = ["userId", "noteId", "chunkIndex"])
@JsonClass(generateAdapter = true)
data class CloudVaultChunkEntity(
    val userId: String,
    val noteId: String,
    val chunkIndex: Int,
    val content: String
)

@Entity(tableName = "account_vault")
@JsonClass(generateAdapter = true)
data class AccountVaultEntity(
    @PrimaryKey val email: String,
    val userId: String,
    val displayName: String,
    val passwordHash: String,
    val provider: String, // "email" or "google"
    val createdAt: String
)

data class NoteModel(
    val id: String,
    val userId: String,
    val title: String,
    val folder: String,
    val sections: List<NoteSection>,
    val isChunked: Boolean = false,
    val chunkCount: Int = 0,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val pinHash: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val needsCloudSync: Boolean = false,
    val syncUpdatedAt: String = updatedAt
) {
    val totalWords: Int
        get() = sections.sumOf { section ->
            val tWords = if (section.title.isBlank()) 0 else section.title.trim().split(Regex("\\s+")).size
            val cWords = if (section.content.isBlank()) 0 else section.content.trim().split(Regex("\\s+")).size
            tWords + cWords
        }

    val previewSnippet: String
        get() {
            val firstNonEmpty = sections.firstOrNull { it.content.isNotBlank() || it.title.isNotBlank() }
            if (firstNonEmpty == null) return "খালি নোট..."
            val combined = buildString {
                if (firstNonEmpty.title.isNotBlank()) append(firstNonEmpty.title).append(" — ")
                append(firstNonEmpty.content)
            }
            return combined.replace(Regex("[#*`>\\$\\|]"), "").trim().take(140)
        }
}

data class UserSession(
    val userId: String,
    val email: String,
    val displayName: String,
    val provider: String
)
