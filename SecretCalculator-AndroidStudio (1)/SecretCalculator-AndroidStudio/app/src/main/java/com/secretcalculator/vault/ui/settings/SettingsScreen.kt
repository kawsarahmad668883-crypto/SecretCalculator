package com.secretcalculator.vault.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.secretcalculator.vault.BuildConfig
import com.secretcalculator.vault.data.preferences.AppThemeMode
import com.secretcalculator.vault.data.preferences.AutoLockPolicy
import com.secretcalculator.vault.ui.theme.VividOrange
import com.secretcalculator.vault.ui.vault.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onResetPin: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val autoLockPolicy by viewModel.autoLockPolicy.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val totalBytes by viewModel.totalEncryptedBytes.collectAsState()
    val photos by viewModel.filteredPhotos.collectAsState()
    val trashed by viewModel.trashedPhotos.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.statusMessage.collect { snackbarHostState.showSnackbar(it) }
    }

    var showChangePinDialog by remember { mutableStateOf(false) }
    var showResetPinDialog by remember { mutableStateOf(false) }
    var showManageAlbumsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExportPassphrase by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && pendingExportPassphrase.isNotEmpty()) {
            viewModel.exportBackup(uri, pendingExportPassphrase)
            pendingExportPassphrase = ""
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Vault Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader("Security")
            }
            item {
                SettingsRowCard(
                    icon = Icons.Outlined.Pin,
                    title = "Change Secret PIN",
                    subtitle = "Requires current PIN verification",
                    onClick = { showChangePinDialog = true }
                )
            }
            item {
                SettingsRowCard(
                    icon = Icons.Outlined.LockReset,
                    title = "Reset Secret PIN",
                    subtitle = "Clear current PIN and set a new one",
                    onClick = { showResetPinDialog = true }
                )
            }
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Auto-Lock Behavior", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        AutoLockPolicy.entries.forEach { policy ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = autoLockPolicy == policy,
                                    onClick = { viewModel.setAutoLockPolicy(policy) }
                                )
                                Text(policy.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Appearance") }
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Calculator & Vault Theme", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Organization & Storage") }
            item {
                SettingsRowCard(
                    icon = Icons.Outlined.FolderOpen,
                    title = "Manage Albums",
                    subtitle = "${albums.size} custom album(s)",
                    onClick = { showManageAlbumsDialog = true }
                )
            }
            item {
                val mb = totalBytes / (1024.0 * 1024.0)
                SettingsRowCard(
                    icon = Icons.Outlined.Storage,
                    title = "Encrypted Storage Usage",
                    subtitle = String.format("%.2f MB across %d active and %d trashed photo(s)", mb, photos.size, trashed.size),
                    onClick = {}
                )
            }

            item { SectionHeader("Encrypted Backup & Restore") }
            item {
                SettingsRowCard(
                    icon = Icons.Outlined.CloudDownload,
                    title = "Export Encrypted Backup (.scvault)",
                    subtitle = "Protected by PBKDF2-SHA256 + AES-256-GCM passphrase",
                    onClick = { showExportDialog = true }
                )
            }
            item {
                SettingsRowCard(
                    icon = Icons.Outlined.RestorePage,
                    title = "Restore from Encrypted Backup",
                    subtitle = "Requires secret PIN + backup recovery passphrase",
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) }
                )
            }

            item { SectionHeader("Privacy & About") }
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Zero-Knowledge Offline Architecture", style = MaterialTheme.typography.titleMedium, color = VividOrange)
                        Text(
                            "• No internet permission in AndroidManifest.\n" +
                                "• AES-256-GCM master key sealed inside hardware Android Keystore.\n" +
                                "• Decrypted image bytes stay strictly in RAM; never written to temp files.\n" +
                                "• FLAG_SECURE blocks screenshots and task-switcher previews inside the vault.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Text(
                            "Secret Calculator · v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showChangePinDialog) {
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var errorText by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = { Text("Change Secret PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { currentPin = it.filter(Char::isDigit).take(8) },
                        label = { Text("Current PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
                        label = { Text("New PIN (4–8 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val res = viewModel.changePin(currentPin, newPin)
                    res.onSuccess { showChangePinDialog = false }
                        .onFailure { errorText = it.message }
                }) { Text("Update PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetPinDialog) {
        AlertDialog(
            onDismissRequest = { showResetPinDialog = false },
            title = { Text("Reset Secret PIN?") },
            text = {
                Text("Your current PIN will be cleared and you will be asked to create a new secret PIN. Your encrypted photos remain safe.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetPinDialog = false
                    viewModel.resetPin()
                    onResetPin()
                }) { Text("Reset PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showResetPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showManageAlbumsDialog) {
        AlertDialog(
            onDismissRequest = { showManageAlbumsDialog = false },
            title = { Text("Manage Albums") },
            text = {
                if (albums.isEmpty()) {
                    Text("No custom albums created yet.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        albums.forEach { album ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(album.name, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.deleteAlbum(album.id) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete album")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageAlbumsDialog = false }) { Text("Done") }
            }
        )
    }

    if (showExportDialog) {
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Encrypted Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Warning: This backup is encrypted with the passphrase below. If you lose or forget this passphrase, recovery is mathematically impossible.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Backup passphrase (min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passphrase.length >= 6,
                    onClick = {
                        pendingExportPassphrase = passphrase
                        showExportDialog = false
                        exportLauncher.launch("secret-vault-backup.scvault")
                    }
                ) { Text("Choose Destination") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingRestoreUri?.let { uri ->
        var pin by remember { mutableStateOf("") }
        var passphrase by remember { mutableStateOf("") }
        var replaceExisting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Authenticate & Restore") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Losing the backup encryption passphrase makes recovery impossible. Verify your secret PIN and enter the backup passphrase.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                        label = { Text("Secret Vault PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Backup Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = replaceExisting, onCheckedChange = { replaceExisting = it })
                        Text("Replace existing vault contents")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pin.length >= 4 && passphrase.isNotEmpty(),
                    onClick = {
                        viewModel.restoreBackup(uri, pin, passphrase, replaceExisting)
                        pendingRestoreUri = null
                    }
                ) { Text("Restore Vault") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = VividOrange,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = VividOrange)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
