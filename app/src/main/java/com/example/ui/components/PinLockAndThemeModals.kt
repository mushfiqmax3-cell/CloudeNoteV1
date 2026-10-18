package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.LocalAppThemeMode
import com.example.util.TextNormalizer

@Composable
fun ThemeSelectorDialog(
    currentTheme: AppThemeMode,
    onSelectTheme: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val active = LocalAppThemeMode.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = active.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, active.border, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "থিম নির্বাচন করুন (৭টি মোড)",
                    style = MaterialTheme.typography.titleMedium,
                    color = active.text,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                AppThemeMode.entries.forEach { mode ->
                    val isSelected = mode == currentTheme
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) active.bgAlt else active.surface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) active.accent else active.border,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                onSelectTheme(mode)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("theme_option_${mode.id}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(mode.bg)
                                .border(1.5.dp, mode.text, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.labelBn,
                                color = active.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = mode.labelEn,
                                color = active.muted,
                                fontSize = 11.sp
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = active.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class PinModalMode {
    VERIFY_TO_OPEN,
    SET_OR_CHANGE
}

@Composable
fun PinLockModal(
    mode: PinModalMode,
    existingPinHash: String?,
    noteTitle: String,
    onPinVerified: () -> Unit,
    onPinUpdated: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAppThemeMode.current
    var pinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = theme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, theme.border, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "PIN Lock",
                    tint = theme.accent,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (mode == PinModalMode.VERIFY_TO_OPEN) "পিন লক খুলুন" else "৪-ডিজিট পিন সেট করুন",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.text,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = noteTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.muted,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { raw ->
                        if (raw.length <= 4) {
                            pinInput = raw
                            errorText = null
                        }
                    },
                    label = { Text("৪ সংখ্যার পিন (০-৯ বা 0-9)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_input_field")
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("বাতিল", color = theme.text)
                    }

                    Button(
                        onClick = {
                            val normalized = TextNormalizer.normalizePinDigits(pinInput)
                            if (normalized.length != 4) {
                                errorText = "সঠিক ৪ সংখ্যার পিন লিখুন"
                                return@Button
                            }
                            if (mode == PinModalMode.VERIFY_TO_OPEN) {
                                if (normalized == existingPinHash) {
                                    onPinVerified()
                                } else {
                                    errorText = "ভুল পিন দিয়েছেন"
                                }
                            } else {
                                onPinUpdated(normalized)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.accent,
                            contentColor = theme.accentFg
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pin_submit_button")
                    ) {
                        Text(if (mode == PinModalMode.VERIFY_TO_OPEN) "খুলুন" else "সেভ করুন")
                    }
                }

                if (mode == PinModalMode.SET_OR_CHANGE && !existingPinHash.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onPinUpdated(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("remove_pin_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LockOpen,
                            contentDescription = "Remove PIN",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("পিন লক সরান", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
