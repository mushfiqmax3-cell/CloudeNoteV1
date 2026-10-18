package com.example.util

import com.example.data.NoteChunkEntity
import com.example.data.NoteSection
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object StorageChunking {

    const val MAX_FIRESTORE_INLINE_BYTES = 550 * 1024 // 550KB threshold
    const val CHUNK_CHAR_SIZE = 150_000 // 150,000 characters per chunk

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val sectionListType = Types.newParameterizedType(List::class.java, NoteSection::class.java)
    private val sectionsAdapter = moshi.adapter<List<NoteSection>>(sectionListType)

    fun serializeSections(sections: List<NoteSection>): String {
        return sectionsAdapter.toJson(sections)
    }

    fun deserializeSections(json: String): List<NoteSection> {
        if (json.isBlank()) {
            return listOf(NoteSection(id = "sec_1", title = "", content = ""))
        }
        return try {
            sectionsAdapter.fromJson(json) ?: listOf(NoteSection(id = "sec_1", title = "", content = ""))
        } catch (e: Exception) {
            listOf(NoteSection(id = "sec_1", title = "", content = json))
        }
    }

    data class ChunkResult(
        val isChunked: Boolean,
        val inlineJson: String,
        val chunks: List<NoteChunkEntity>
    )

    /**
     * Checks if serialized sections exceed 550KB (MAX_FIRESTORE_INLINE_BYTES).
     * If so, splits into 150,000-character chunks for /note_chunks/chunk_{i}.
     */
    fun prepareNoteStorage(noteId: String, sections: List<NoteSection>): ChunkResult {
        val fullJson = serializeSections(sections)
        val byteSize = fullJson.toByteArray(Charsets.UTF_8).size
        if (byteSize <= MAX_FIRESTORE_INLINE_BYTES) {
            return ChunkResult(
                isChunked = false,
                inlineJson = fullJson,
                chunks = emptyList()
            )
        }

        val chunkEntities = mutableListOf<NoteChunkEntity>()
        var index = 0
        var offset = 0
        while (offset < fullJson.length) {
            val end = minOf(offset + CHUNK_CHAR_SIZE, fullJson.length)
            val slice = fullJson.substring(offset, end)
            chunkEntities.add(
                NoteChunkEntity(
                    noteId = noteId,
                    chunkIndex = index,
                    content = slice
                )
            )
            index++
            offset = end
        }

        return ChunkResult(
            isChunked = true,
            inlineJson = "",
            chunks = chunkEntities
        )
    }

    /**
     * Seamlessly reassembles chunked notes in memory (hydrateNoteInMemory).
     */
    fun hydrateNoteInMemory(
        isChunked: Boolean,
        inlineJson: String,
        chunks: List<NoteChunkEntity>
    ): List<NoteSection> {
        val rawJson = if (!isChunked || chunks.isEmpty()) {
            inlineJson
        } else {
            chunks.sortedBy { it.chunkIndex }.joinToString("") { it.content }
        }
        return deserializeSections(rawJson)
    }
}
