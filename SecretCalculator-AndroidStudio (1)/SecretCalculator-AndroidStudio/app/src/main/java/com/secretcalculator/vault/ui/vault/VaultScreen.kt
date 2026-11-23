package com.secretcalculator.vault.ui.vault

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secretcalculator.vault.data.local.entity.AlbumEntity
import com.secretcalculator.vault.data.local.entity.VaultPhotoEntity
import com.secretcalculator.vault.data.preferences.SortOrder
import com.secretcalculator.vault.ui.theme.VividOrange

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onOpenPhoto: (String) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
    onLockVault: () -> Unit
) {
    val photos by viewModel.filteredPhotos.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val selectedAlbumId by viewModel.selectedAlbumId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val selectedIds by viewModel.selectedPhotoIds.collectAsState()
    val trashedCount by viewModel.trashedPhotos.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.statusMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importPhotos(uris)
        }
    }

    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var albumToRename by remember { mutableStateOf<AlbumEntity?>(null) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val matchingAlbums = remember(albums, searchQuery) {
        if (searchQuery.isBlank()) albums
        else albums.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectedIds.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMoveSheet = true }) {
                            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "Move to album")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Move to Trash")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "Private Vault",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenTrash) {
                            BadgedBox(
                                badge = {
                                    if (trashedCount.isNotEmpty()) {
                                        Badge { Text("${trashedCount.size}") }
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Trash")
                            }
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Outlined.SwapVert, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label) },
                                        trailingIcon = {
                                            if (sortOrder == order) {
                                                Icon(Icons.Outlined.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = onLockVault) {
                            Icon(Icons.Outlined.Lock, contentDescription = "Lock Vault", tint = VividOrange)
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                containerColor = VividOrange,
                contentColor = Color.White,
                icon = { Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null) },
                text = { Text("Import Photos") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isBusy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = VividOrange
                )
            }

            // Search photos and albums
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search encrypted photos or albums") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Albums carousel
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedAlbumId == null,
                        onClick = { viewModel.selectAlbum(null) },
                        label = { Text("All Photos") }
                    )
                }
                items(matchingAlbums, key = { it.id }) { album ->
                    FilterChip(
                        selected = selectedAlbumId == album.id,
                        onClick = { viewModel.selectAlbum(album.id) },
                        label = { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = if (selectedAlbumId == album.id) {
                            {
                                IconButton(
                                    onClick = { albumToRename = album },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Rename album", modifier = Modifier.size(14.dp))
                                }
                            }
                        } else null
                    )
                }
                item {
                    AssistChip(
                        onClick = { showCreateAlbumDialog = true },
                        label = { Text("New Album") },
                        leadingIcon = {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }

            if (photos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No encrypted photos here yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap Import Photos to encrypt images from your gallery with AES-256-GCM.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(photos, key = { it.id }) { photo ->
                        val isSelected = photo.id in selectedIds
                        EncryptedThumbTile(
                            photo = photo,
                            isSelected = isSelected,
                            loadThumb = { viewModel.loadDecryptedThumbnail(photo) },
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    viewModel.togglePhotoSelection(photo.id)
                                } else {
                                    onOpenPhoto(photo.id)
                                }
                            },
                            onLongClick = { viewModel.togglePhotoSelection(photo.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateAlbumDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateAlbumDialog = false },
            title = { Text("Create Album") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Album name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createAlbum(name)
                        showCreateAlbumDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAlbumDialog = false }) { Text("Cancel") }
            }
        )
    }

    albumToRename?.let { album ->
        var newName by remember(album.id) { mutableStateOf(album.name) }
        AlertDialog(
            onDismissRequest = { albumToRename = null },
            title = { Text("Rename Album") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Album name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameAlbum(album.id, newName)
                        albumToRename = null
                    },
                    enabled = newName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { albumToRename = null }) { Text("Cancel") }
            }
        )
    }

    if (showMoveSheet) {
        ModalBottomSheet(onDismissRequest = { showMoveSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Text("Move ${selectedIds.size} photo(s) to Album", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                ListTileOption(
                    label = "All Photos (No Album)",
                    onClick = {
                        viewModel.moveSelectedToAlbum(null)
                        showMoveSheet = false
                    }
                )
                albums.forEach { album ->
                    ListTileOption(
                        label = album.name,
                        onClick = {
                            viewModel.moveSelectedToAlbum(album.id)
                            showMoveSheet = false
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Move to Trash?") },
            text = { Text("${selectedIds.size} photo(s) will be moved to Trash. You can restore or permanently wipe them later.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.moveSelectedToTrash()
                    showDeleteConfirm = false
                }) { Text("Move to Trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EncryptedThumbTile(
    photo: VaultPhotoEntity,
    isSelected: Boolean,
    loadThumb: suspend () -> ByteArray?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var bitmap by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photo.id) {
        val bytes = loadThumb()
        if (bytes != null) {
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bitmap = decoded?.asImageBitmap()
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (isSelected) Modifier.border(2.5.dp, VividOrange, RoundedCornerShape(10.dp))
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        bitmap?.let { img ->
            Image(
                bitmap = img,
                contentDescription = photo.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(VividOrange),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ListTileOption(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = VividOrange)
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
