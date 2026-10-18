package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.components.CloudNoteLogo
import com.example.ui.theme.LocalAppThemeMode

@Composable
fun LoginScreen(
    isOnline: Boolean,
    onLoginOrRegister: (email: String, password: String, displayName: String?, isRegister: Boolean, onError: (String) -> Unit) -> Unit,
    onGoogleSignIn: (email: String, displayName: String, onError: (String) -> Unit) -> Unit,
    onOpenThemeSelector: () -> Unit
) {
    val theme = LocalAppThemeMode.current
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showGoogleDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        IconButton(
            onClick = onOpenThemeSelector,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(48.dp)
                .testTag("login_theme_button")
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = "Theme Selector",
                tint = theme.text
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CloudNoteLogo(size = 56.dp, isOnline = isOnline)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "CloudNote Studio",
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.text,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isRegisterMode) "নতুন অ্যাকাউন্ট তৈরি" else "অ্যাকাউন্ট লগইন",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.muted
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Mandatory First-Time Online Alert when offline
                if (!isOnline) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFEF2F2))
                            .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                            .testTag("offline_login_warning")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Offline",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "প্রথমবার লগইনের জন্য ইন্টারনেট সংযোগ চালু করুন",
                            color = Color(0xFF991B1B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Mode Switch Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.bgAlt)
                        .border(1.dp, theme.border, RoundedCornerShape(10.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isRegisterMode) theme.surface else Color.Transparent)
                            .clickable {
                                isRegisterMode = false
                                errorMsg = null
                            }
                            .testTag("tab_login")
                    ) {
                        Text(
                            text = "লগইন",
                            color = theme.text,
                            fontWeight = if (!isRegisterMode) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isRegisterMode) theme.surface else Color.Transparent)
                            .clickable {
                                isRegisterMode = true
                                errorMsg = null
                            }
                            .testTag("tab_register")
                    ) {
                        Text(
                            text = "রেজিস্ট্রেশন",
                            color = theme.text,
                            fontWeight = if (isRegisterMode) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {
                            displayName = it
                            errorMsg = null
                        },
                        label = { Text("নাম") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.AccountCircle, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_name_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMsg = null
                    },
                    label = { Text("ইমেইল") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = {
                        Icon(Icons.Default.MailOutline, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_email_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMsg = null
                    },
                    label = { Text("পাসওয়ার্ড") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        onLoginOrRegister(email, password, displayName, isRegisterMode) { err ->
                            errorMsg = err
                        }
                    },
                    enabled = isOnline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accent,
                        contentColor = theme.accentFg
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("login_submit_button")
                ) {
                    Text(
                        text = if (isRegisterMode) "অ্যাকাউন্ট তৈরি করুন" else "লগইন করুন",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = theme.border)
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { showGoogleDialog = true },
                    enabled = isOnline,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("google_signin_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Google Sign-In",
                        tint = theme.text,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google দিয়ে সাইন-ইন",
                        color = theme.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    if (showGoogleDialog) {
        var gEmail by remember { mutableStateOf("mushfiqxenon@gmail.com") }
        var gName by remember { mutableStateOf("Mushfiq Xenon") }

        Dialog(onDismissRequest = { showGoogleDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = theme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.border, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Google অ্যাকাউন্ট নির্বাচন",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.text,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = gName,
                        onValueChange = { gName = it },
                        label = { Text("প্রোফাইল নাম") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gEmail,
                        onValueChange = { gEmail = it },
                        label = { Text("Google ইমেইল") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showGoogleDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("বাতিল", color = theme.text)
                        }
                        Button(
                            onClick = {
                                showGoogleDialog = false
                                onGoogleSignIn(gEmail, gName) { err ->
                                    errorMsg = err
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.accent,
                                contentColor = theme.accentFg
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_google_signin_button")
                        ) {
                            Text("কন্টিনিউ")
                        }
                    }
                }
            }
        }
    }
}
