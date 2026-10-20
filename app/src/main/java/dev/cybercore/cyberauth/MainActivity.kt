package dev.cybercore.cyberauth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

private val Ink = Color(0xFF080B12)
private val Panel = Color(0xFF111722)
private val Line = Color(0xFF273343)
private val Cyan = Color(0xFF35E4EC)
private val Violet = Color(0xFFB579FF)
private val Muted = Color(0xFF99A8B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = AccountStore(this)
        setContent { CyberAuthScreen(store) }
    }
}

@Composable
private fun CyberAuthScreen(store: AccountStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialLoad = remember { runCatching(store::load) }
    var accounts by remember { mutableStateOf(initialLoad.getOrDefault(emptyList())) }
    var loadError by remember { mutableStateOf(initialLoad.exceptionOrNull()?.message) }
    var showAdd by remember { mutableStateOf(false) }
    var showExportPassphrase by remember { mutableStateOf(false) }
    var showImportPassphrase by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { tick = System.currentTimeMillis() / 1000; delay(250) } }

    fun update(next: List<TotpAccount>): Boolean {
        if (loadError != null) return false
        return runCatching { store.save(next) }.onSuccess {
            accounts = next
            message = "Encrypted accounts saved"
        }.onFailure { message = it.message ?: "Could not save encrypted account data" }.isSuccess
    }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.cyberauth.backup")) { destination: Uri? ->
        val encryptedFile = pendingExport
        pendingExport = null
        if (destination == null || encryptedFile == null) {
            encryptedFile?.fill(0)
            message = "Backup export canceled"
        } else {
            busy = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "w")?.use { it.write(encryptedFile) }
                            ?: error("Could not write the selected backup file")
                    }
                }.onSuccess { message = "Encrypted backup saved" }
                    .onFailure { message = it.message ?: "Backup export failed" }
                encryptedFile.fill(0)
                busy = false
            }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source: Uri? ->
        if (source != null) {
            busy = true
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(source)?.use { readLimited(it, EncryptedBackup.MAX_FILE_BYTES) }
                            ?: error("Could not read the selected backup file")
                    }
                }.onSuccess { pendingImport = it; showImportPassphrase = true }
                    .onFailure { message = it.message ?: "Could not read the backup" }
                busy = false
            }
        }
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scanned = result.data?.getStringExtra(QrScannerActivity.EXTRA_OTP_URI)
        if (result.resultCode == android.app.Activity.RESULT_OK && scanned != null) {
            runCatching { Totp.parseOtpAuth(scanned).copy(id = UUID.randomUUID().toString()) }
                .onSuccess { account ->
                    val duplicate = accounts.any { it.issuer == account.issuer && it.label == account.label && it.secret == account.secret }
                    if (duplicate) message = "That account is already in CyberAuth"
                    else if (update(accounts + account)) message = "Authenticator account added from QR"
                }
                .onFailure { message = it.message ?: "That QR code is not a supported TOTP setup code" }
        } else {
            message = result.data?.getStringExtra(QrScannerActivity.EXTRA_SCAN_ERROR) ?: "QR scan canceled"
        }
    }

    fun beginExport(passphrase: CharArray) {
        showExportPassphrase = false
        busy = true
        scope.launch {
            try {
                val encryptedFile = withContext(Dispatchers.IO) { EncryptedBackup.encrypt(accounts, passphrase) }
                pendingExport = encryptedFile
                createBackup.launch("cyberauth-backup.cyberauth")
            } catch (failure: Exception) {
                message = failure.message ?: "Could not encrypt the backup"
            } finally {
                passphrase.fill('\u0000')
                busy = false
            }
        }
    }

    fun beginImport(passphrase: CharArray) {
        showImportPassphrase = false
        val encryptedFile = pendingImport
        pendingImport = null
        if (encryptedFile == null) { passphrase.fill('\u0000'); return }
        busy = true
        scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) { EncryptedBackup.decrypt(encryptedFile, passphrase) }
                val merged = (accounts + imported).distinctBy { "${it.issuer}\u0000${it.label}\u0000${it.secret}" }
                val added = merged.size - accounts.size
                if (update(merged)) message = "Imported $added account${if (added == 1) "" else "s"}; existing accounts were kept"
            } catch (failure: Exception) {
                message = failure.message?.takeIf { it.startsWith("Wrong backup") || it.startsWith("This backup") || it.startsWith("This is not") }
                    ?: "Could not decrypt this backup. Check the passphrase and file."
            } finally {
                passphrase.fill('\u0000')
                encryptedFile.fill(0)
                busy = false
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Violet, background = Ink, surface = Panel, onPrimary = Ink, onBackground = Color(0xFFEAF1F7), onSurface = Color(0xFFEAF1F7))) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = Color(0xFF14242C), shape = RoundedCornerShape(14.dp), modifier = Modifier.size(48.dp)) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Text("⌘", color = Cyan, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("CYBERAUTH", color = Cyan, fontSize = 12.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Bold)
                        Text("Your codes. On your device.", color = Muted, fontSize = 13.sp)
                    }
                    Icon(Icons.Default.Lock, contentDescription = "Encrypted local storage", tint = Violet)
                }
                Spacer(Modifier.height(26.dp))
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("AUTHENTICATOR", color = Muted, fontSize = 11.sp, letterSpacing = 1.7.sp)
                        Text("One-time codes", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("${accounts.size} ${if (accounts.size == 1) "ACCOUNT" else "ACCOUNTS"}", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { showAdd = true }, enabled = loadError == null && !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text(" ADD", maxLines = 1) }
                    OutlinedButton(onClick = { showExportPassphrase = true }, enabled = accounts.isNotEmpty() && loadError == null && !busy, modifier = Modifier.weight(1f)) { Text("EXPORT", maxLines = 1) }
                    OutlinedButton(onClick = { openBackup.launch(arrayOf("*/*")) }, enabled = loadError == null && !busy, modifier = Modifier.weight(1f)) { Text("IMPORT", maxLines = 1) }
                }
                Spacer(Modifier.height(16.dp))
                if (loadError != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                            Text("LOCAL STORE COULD NOT BE OPENED", color = Color(0xFFFF8585), fontSize = 11.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Text("CyberAuth has not changed or replaced your encrypted account data.", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(loadError ?: "The device key may be unavailable.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                } else if (accounts.isEmpty()) {
                    EmptyState(Modifier.weight(1f), onAdd = { showAdd = true })
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        items(accounts, key = { it.id }) { account ->
                            AccountCard(account, tick,
                                onCopy = { code ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("CyberAuth code", code))
                                    message = "Code copied"
                                },
                                onDelete = { update(accounts.filterNot { it.id == account.id }) })
                        }
                    }
                }
                if (message != null) {
                    Text(message!!, color = Violet, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    LaunchedEffect(message) { delay(2400); message = null }
                }
                Text("OFFLINE TOTP  ·  ENCRYPTED DEVICE STORAGE  ·  NO INTERNET ACCESS", color = Muted, fontSize = 9.sp, letterSpacing = 0.7.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp))
            }
        }
    }
    if (showAdd) AddAccountDialog(
        onDismiss = { showAdd = false },
        onScan = { showAdd = false; scanner.launch(Intent(context, QrScannerActivity::class.java)) },
        onAdd = { account -> if (update(accounts + account)) showAdd = false },
    )
    if (showExportPassphrase) BackupPassphraseDialog(
        title = "Encrypt backup",
        description = "Create a separate passphrase with at least 12 characters. You will need it to restore this file on another device. CyberAuth cannot recover it.",
        confirmLabel = "CONTINUE TO SAVE",
        confirmationRequired = true,
        onDismiss = { showExportPassphrase = false },
        onConfirm = ::beginExport,
    )
    if (showImportPassphrase) BackupPassphraseDialog(
        title = "Unlock backup",
        description = "Enter the passphrase that was used to encrypt this CyberAuth backup. Imported accounts will be merged; existing entries are kept.",
        confirmLabel = "DECRYPT + MERGE",
        confirmationRequired = false,
        onDismiss = { showImportPassphrase = false; pendingImport?.fill(0); pendingImport = null },
        onConfirm = ::beginImport,
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("◉", color = Cyan, fontSize = 46.sp)
            Spacer(Modifier.height(12.dp))
            Text("Your authenticator starts here", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text("Add an account with its setup key or an otpauth link. Your secret stays encrypted on this device.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) { Icon(Icons.Default.Add, null); Text(" Add account", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun AccountCard(account: TotpAccount, tick: Long, onCopy: (String) -> Unit, onDelete: () -> Unit) {
    val left = account.period - (tick % account.period).toInt()
    val code = remember(account.secret, account.algorithm, account.digits, account.period, tick / account.period) {
        runCatching { Totp.code(account.secret, tick, account.algorithm, account.digits, account.period) }.getOrDefault("------")
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(account.issuer.ifBlank { "Authenticator" }, color = Cyan, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                    Text(account.label, color = Muted, fontSize = 13.sp)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Remove ${account.label}", tint = Muted) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(code.chunked(3).joinToString(" "), modifier = Modifier.weight(1f), fontSize = 31.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text("${left}s", color = if (left <= 5) Violet else Muted, fontSize = 12.sp)
                IconButton(onClick = { if (code != "------") onCopy(code) }) { Icon(Icons.Default.ContentCopy, "Copy code", tint = Cyan) }
            }
            androidx.compose.material3.LinearProgressIndicator(progress = { left.toFloat() / account.period }, modifier = Modifier.fillMaxWidth(), color = Cyan, trackColor = Line)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onScan: () -> Unit, onAdd: (TotpAccount) -> Unit) {
    var issuer by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add authenticator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scan a service QR code or enter its setup key. Scanning happens on this device; the camera is used only for this step.", color = Muted, fontSize = 13.sp)
                OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("SCAN QR CODE") }
                OutlinedTextField(issuer, { issuer = it }, label = { Text("Service / issuer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(label, { label = it }, label = { Text("Account name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(secret, { secret = it }, label = { Text("Base32 setup key") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(uri, { uri = it }, label = { Text("Or paste otpauth link") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = Color(0xFFFF8585), fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = {
            runCatching {
                if (uri.isNotBlank()) Totp.parseOtpAuth(uri).copy(id = UUID.randomUUID().toString())
                else TotpAccount(UUID.randomUUID().toString(), issuer.trim(), label.trim().ifBlank { issuer.trim() }, secret.trim()).also { require(issuer.isNotBlank() && secret.isNotBlank()) { "Enter a service and setup key" }; Totp.decodeBase32(secret) }
            }.onSuccess(onAdd).onFailure { error = it.message ?: "Check the account details" }
        }) { Text("ADD", color = Cyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Muted) } },
        containerColor = Panel,
    )
}

@Composable
private fun BackupPassphraseDialog(
    title: String,
    description: String,
    confirmLabel: String,
    confirmationRequired: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                OutlinedTextField(passphrase, { passphrase = it; error = null }, label = { Text("Backup passphrase") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (confirmationRequired) OutlinedTextField(confirmation, { confirmation = it; error = null }, label = { Text("Confirm passphrase") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = Color(0xFFFF8585), fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = {
            when {
                passphrase.length < EncryptedBackup.MIN_PASSPHRASE_CHARS -> error = "Use at least ${EncryptedBackup.MIN_PASSPHRASE_CHARS} characters"
                confirmationRequired && passphrase != confirmation -> error = "The passphrases do not match"
                else -> {
                    val chars = passphrase.toCharArray()
                    passphrase = ""
                    confirmation = ""
                    onConfirm(chars)
                }
            }
        }) { Text(confirmLabel, color = Cyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Muted) } },
        containerColor = Panel,
    )
}

private fun readLimited(input: java.io.InputStream, maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(chunk)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "Backup file is larger than the supported limit" }
        output.write(chunk, 0, count)
    }
    return output.toByteArray()
}
