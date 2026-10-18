package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NoteModel
import com.example.ui.components.CloudNoteLogo
import com.example.ui.theme.LocalAppThemeMode
import com.example.util.TextNormalizer
import com.example.viewmodel.DrawerFilter

@Composable
fun NoteListScreen(
    notes: List<NoteModel>,
    isOnline: Boolean,
    isSyncing: Boolean,
    drawerFilter: DrawerFilter,
    selectedFolder: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenThemeSelector: () -> Unit,
    onCreateNewNote: () -> Unit,
    onOpenNote: (NoteModel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMoveToTrash: (String) -> Unit,
    onRestoreFromTrash: (String) -> Unit,
    onDeleteForever: (String) -> Unit
) {
    val theme = LocalAppThemeMode.current

    val screenTitle = when (drawerFilter) {
        DrawerFilter.ALL_NOTES -> "সকল নোট"
        DrawerFilter.FAVORITES -> "প্রিয় নোট"
        DrawerFilter.TRASH -> "ট্র্যাশ"
        DrawerFilter.FOLDER -> selectedFolder
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
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Menu",
                            tint = theme.text
                        )
                    }

                    CloudNoteLogo(size = 28.dp, isOnline = isOnline)
                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = screenTitle,
                            color = theme.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isOnline) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    isSyncing -> "ক্লাউডে সিঙ্ক হচ্ছে..."
                                    isOnline -> "অনলাইন সিঙ্ক সক্রিয়"
                                    else -> "অফলাইন (ফোনে সেভ হচ্ছে)"
                                },
                                color = theme.muted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenThemeSelector,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("top_theme_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "থিম পরিবর্তন",
                            tint = theme.text
                        )
                    }
                }

                // Minimalist Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("নোট বা সেকশন খুঁজুন...", fontSize = 14.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = theme.muted)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = theme.muted)
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("search_notes_input")
                )

                HorizontalDivider(color = theme.border, thickness = 1.dp)
            }
        },
        floatingActionButton = {
            if (drawerFilter != DrawerFilter.TRASH) {
                FloatingActionButton(
                    onClick = onCreateNewNote,
                    containerColor = theme.accent,
                    contentColor = theme.accentFg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("fab_new_note")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Note")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "নতুন নোট",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CloudNoteLogo(size = 48.dp, isOnline = isOnline)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "কোনো নোট পাওয়া যায়নি",
                        color = theme.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteItemCard(
                        note = note,
                        onClick = { onOpenNote(note) },
                        onToggleFavorite = { onToggleFavorite(note.id) },
                        onMoveToTrash = { onMoveToTrash(note.id) },
                        onRestoreFromTrash = { onRestoreFromTrash(note.id) },
                        onDeleteForever = { onDeleteForever(note.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteItemCard(
    note: NoteModel,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestoreFromTrash: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val theme = LocalAppThemeMode.current
    val isLocked = !note.pinHash.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.surface)
            .border(1.dp, theme.border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp)
            .testTag("note_card_${note.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLocked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "পিন লক করা",
                    tint = theme.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = note.title,
                color = theme.text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Unsynced offline badge icon (CloudOff)
            if (note.needsCloudSync) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "অফলাইনে সেভ করা (সিঙ্ক বাকি)",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(16.dp)
                        .testTag("unsynced_icon_${note.id}")
                )
            }

            if (!note.isTrashed) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("fav_btn_${note.id}")
                ) {
                    Icon(
                        imageVector = if (note.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "প্রিয় নোট",
                        tint = if (note.isFavorite) Color(0xFFEAB308) else theme.muted,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isLocked) "•••••••• পিন দ্বারা সুরক্ষিত নোট" else note.previewSnippet,
            color = theme.muted,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Folder Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.bgAlt)
                        .border(1.dp, theme.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = theme.muted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = note.folder,
                        color = theme.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "${TextNormalizer.toBengaliDigits(note.sections.size)} সেকশন • ${TextNormalizer.toBengaliDigits(note.totalWords)} শব্দ",
                    color = theme.muted,
                    fontSize = 11.sp
                )

                if (note.isChunked) {
                    Text(
                        text = "• চ্যাঙ্কড (${TextNormalizer.toBengaliDigits(note.chunkCount)})",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (note.isTrashed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRestoreFromTrash,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestoreFromTrash,
                            contentDescription = "রিস্টোর",
                            tint = theme.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteForever,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "স্থায়ীভাবে মুছুন",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onMoveToTrash,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "ট্র্যাশে সরান",
                        tint = theme.muted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
