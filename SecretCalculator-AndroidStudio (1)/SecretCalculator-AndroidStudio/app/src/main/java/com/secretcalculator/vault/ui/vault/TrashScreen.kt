package com.secretcalculator.vault.ui.vault

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.secretcalculator.vault.data.local.entity.VaultPhotoEntity
import com.secretcalculator.vault.ui.theme.VividOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: VaultViewModel,
    onBack: () -> Unit
) {
    val trashed by viewModel.trashedPhotos.collectAsState()
    var confirmEmptyAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash (${trashed.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trashed.isNotEmpty()) {
                        TextButton(onClick = { confirmEmptyAll = true }) {
                            Text("Wipe All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (trashed.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Trash is empty.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(trashed, key = { it.id }) { photo ->
                    TrashItemRow(
                        photo = photo,
                        loadThumb = { viewModel.loadDecryptedThumbnail(photo) },
                        onRestore = { viewModel.restoreFromTrash(listOf(photo.id)) },
                        onPermanentWipe = { viewModel.permanentlyDelete(listOf(photo.id)) }
                    )
                }
            }
        }
    }

    if (confirmEmptyAll) {
        AlertDialog(
            onDismissRequest = { confirmEmptyAll = false },
            title = { Text("Securely Wipe All Trash?") },
            text = {
                Text("Encrypted blobs will be overwritten with random bytes and permanently erased. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.permanentlyDelete(trashed.map { it.id })
                    confirmEmptyAll = false
                }) {
                    Text("Wipe Permanently", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmptyAll = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TrashItemRow(
    photo: VaultPhotoEntity,
    loadThumb: suspend () -> ByteArray?,
    onRestore: () -> Unit,
    onPermanentWipe: () -> Unit
) {
    var thumb by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }
    var confirmSingleWipe by remember { mutableStateOf(false) }

    LaunchedEffect(photo.id) {
        val bytes = loadThumb()
        if (bytes != null) {
            thumb = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                thumb?.let {
                    Image(
                        bitmap = it,
                        contentDescription = photo.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(photo.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "${photo.encryptedSizeBytes / 1024} KB · Encrypted",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Restore", tint = VividOrange)
            }
            IconButton(onClick = { confirmSingleWipe = true }) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = "Secure Wipe", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmSingleWipe) {
        AlertDialog(
            onDismissRequest = { confirmSingleWipe = false },
            title = { Text("Securely Wipe Photo?") },
            text = { Text("This overwrites and destroys \"${photo.displayName}\" permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    onPermanentWipe()
                    confirmSingleWipe = false
                }) { Text("Wipe", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSingleWipe = false }) { Text("Cancel") }
            }
        )
    }
}
