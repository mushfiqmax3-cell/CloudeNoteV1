package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DEFAULT_FOLDERS
import com.example.data.NoteModel
import com.example.data.UserSession
import com.example.ui.theme.LocalAppThemeMode
import com.example.util.TextNormalizer
import com.example.viewmodel.DrawerFilter

@Composable
fun NavigationDrawerContent(
    session: UserSession,
    isOnline: Boolean,
    allNotes: List<NoteModel>,
    folders: List<String>,
    currentFilter: DrawerFilter,
    selectedFolder: String,
    onSelectFilter: (DrawerFilter, String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onExportBackupJson: () -> Unit,
    onImportBackupJson: () -> Unit,
    onLogout: () -> Unit
) {
    val theme = LocalAppThemeMode.current
    var showNewFolderInput by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val activeNotesCount = allNotes.count { !it.isTrashed }
    val favCount = allNotes.count { !it.isTrashed && it.isFavorite }
    val trashCount = allNotes.count { it.isTrashed }

    Column(
        modifier = Modifier
            .width(305.dp)
            .fillMaxHeight()
            .background(theme.surface)
            .border(1.dp, theme.border)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Brand & Sync Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            CloudNoteLogo(size = 38.dp, isOnline = isOnline)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CloudNote Studio",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = session.email,
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Online / Offline Status Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(theme.bgAlt)
                .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("drawer_sync_status_badge")
        ) {
            Icon(
                imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                contentDescription = null,
                tint = if (isOnline) Color(0xFF10B981) else Color(0xFFF59E0B),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isOnline) "অনলাইন সিঙ্ক সক্রিয়" else "অফলাইন (ফোনে সেভ হচ্ছে)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = theme.text
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = theme.border)
        Spacer(modifier = Modifier.height(12.dp))

        // Primary Filters
        DrawerNavRow(
            icon = Icons.AutoMirrored.Filled.Notes,
            label = "সকল নোট",
            count = activeNotesCount,
            selected = currentFilter == DrawerFilter.ALL_NOTES,
            onClick = { onSelectFilter(DrawerFilter.ALL_NOTES, null) },
            testTag = "drawer_all_notes"
        )
        DrawerNavRow(
            icon = Icons.Default.StarBorder,
            label = "প্রিয় নোট",
            count = favCount,
            selected = currentFilter == DrawerFilter.FAVORITES,
            onClick = { onSelectFilter(DrawerFilter.FAVORITES, null) },
            testTag = "drawer_favorites"
        )
        DrawerNavRow(
            icon = Icons.Default.DeleteOutline,
            label = "ট্র্যাশ",
            count = trashCount,
            selected = currentFilter == DrawerFilter.TRASH,
            onClick = { onSelectFilter(DrawerFilter.TRASH, null) },
            testTag = "drawer_trash"
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = theme.border)
        Spacer(modifier = Modifier.height(10.dp))

        // Folders Header with + (FolderPlus) button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ফোল্ডারসমূহ",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = theme.muted
            )
            IconButton(
                onClick = { showNewFolderInput = !showNewFolderInput },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("drawer_add_folder_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = "নতুন ফোল্ডার তৈরি করুন",
                    tint = theme.text,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Inline Folder Creation Input
        if (showNewFolderInput) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("ফোল্ডারের নাম...", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("drawer_new_folder_input")
                )
                IconButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName.trim())
                            newFolderName = ""
                            showNewFolderInput = false
                        }
                    },
                    modifier = Modifier.testTag("drawer_confirm_folder_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save Folder", tint = theme.accent)
                }
                IconButton(
                    onClick = {
                        newFolderName = ""
                        showNewFolderInput = false
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = theme.muted)
                }
            }
        }

        // Folder Items
        folders.forEach { folderName ->
            val folderNoteCount = allNotes.count { !it.isTrashed && it.folder == folderName }
            val isSelected = currentFilter == DrawerFilter.FOLDER && selectedFolder == folderName
            val isDeletable = folderName != "সাধারণ" && !DEFAULT_FOLDERS.contains(folderName)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) theme.bgAlt else Color.Transparent)
                    .border(
                        width = if (isSelected) 1.dp else 0.dp,
                        color = if (isSelected) theme.border else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectFilter(DrawerFilter.FOLDER, folderName) }
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .testTag("drawer_folder_$folderName")
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isSelected) theme.accent else theme.muted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = folderName,
                    color = theme.text,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = TextNormalizer.toBengaliDigits(folderNoteCount),
                    color = theme.muted,
                    fontSize = 12.sp
                )
                if (isDeletable) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Folder",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onDeleteFolder(folderName) }
                            .testTag("delete_folder_$folderName")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = theme.border)
        Spacer(modifier = Modifier.height(10.dp))

        // Backup & Restore (.json)
        DrawerActionRow(
            icon = Icons.Default.FileDownload,
            label = "ব্যাকআপ ডাউনলোড (.json)",
            onClick = onExportBackupJson,
            testTag = "drawer_backup_export"
        )
        DrawerActionRow(
            icon = Icons.Default.FileUpload,
            label = "ব্যাকআপ রিস্টোর (.json)",
            onClick = onImportBackupJson,
            testTag = "drawer_backup_import"
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = theme.border)
        Spacer(modifier = Modifier.height(8.dp))

        DrawerActionRow(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            label = "লগআউট",
            onClick = onLogout,
            testTag = "drawer_logout_button"
        )
    }
}

@Composable
private fun DrawerNavRow(
    icon: ImageVector,
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val theme = LocalAppThemeMode.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) theme.bgAlt else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) theme.border else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 11.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) theme.accent else theme.muted,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = theme.text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = TextNormalizer.toBengaliDigits(count),
            color = theme.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DrawerActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    val theme = LocalAppThemeMode.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 11.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.muted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = theme.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
