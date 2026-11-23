package com.secretcalculator.vault.ui.vault

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.secretcalculator.vault.ui.theme.ObsidianBlack
import com.secretcalculator.vault.ui.theme.VividOrange
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    photoId: String,
    viewModel: VaultViewModel,
    onBack: () -> Unit
) {
    val photos by viewModel.filteredPhotos.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val photo = photos.find { it.id == photoId }

    var fullBitmap by remember(photoId) { mutableStateOf<ImageBitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var showInfoDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(photo) {
        if (photo != null) {
            val bytes = viewModel.loadDecryptedFullPhoto(photo)
            if (bytes != null) {
                fullBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
    }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBlack.copy(alpha = 0.85f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                title = { Text(photo?.displayName ?: "Photo", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "Details")
                    }
                    IconButton(onClick = { showMoveDialog = true }) {
                        Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "Move to album")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ObsidianBlack)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (fullBitmap != null) {
                Image(
                    bitmap = fullBitmap!!,
                    contentDescription = photo?.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            } else {
                CircularProgressIndicator(color = VividOrange)
            }
        }
    }

    if (showInfoDialog && photo != null) {
        val dateText = SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.getDefault())
            .format(Date(photo.createdAt))
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Encrypted File Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Name: ${photo.displayName}")
                    Text("Dimensions: ${photo.width} × ${photo.height}")
                    Text("Encrypted size: ${photo.encryptedSizeBytes / 1024} KB")
                    Text("Cipher: AES-256-GCM (Android Keystore)")
                    Text("Imported: $dateText")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
            }
        )
    }

    if (showMoveDialog && photo != null) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move to Album") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        onClick = {
                            viewModel.movePhotoToAlbum(photo.id, null)
                            showMoveDialog = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("All Photos", modifier = Modifier.padding(14.dp))
                    }
                    albums.forEach { album ->
                        Surface(
                            onClick = {
                                viewModel.movePhotoToAlbum(photo.id, album.id)
                                showMoveDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(album.name, modifier = Modifier.padding(14.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm && photo != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Move to Trash?") },
            text = { Text("\"${photo.displayName}\" will be moved to Trash.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.movePhotoToTrash(photo.id)
                    showDeleteConfirm = false
                    onBack()
                }) { Text("Move to Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
