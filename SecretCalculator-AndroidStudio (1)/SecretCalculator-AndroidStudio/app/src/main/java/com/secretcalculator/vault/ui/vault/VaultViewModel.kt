package com.secretcalculator.vault.ui.vault

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.secretcalculator.vault.data.local.entity.AlbumEntity
import com.secretcalculator.vault.data.local.entity.VaultPhotoEntity
import com.secretcalculator.vault.data.preferences.AppThemeMode
import com.secretcalculator.vault.data.preferences.AutoLockPolicy
import com.secretcalculator.vault.data.preferences.SortOrder
import com.secretcalculator.vault.data.preferences.VaultPreferences
import com.secretcalculator.vault.data.repository.VaultRepository
import com.secretcalculator.vault.security.PinManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VaultViewModel(
    private val repository: VaultRepository,
    private val preferences: VaultPreferences,
    private val pinManager: PinManager
) : ViewModel() {

    private val _selectedAlbumId = MutableStateFlow<String?>(null) // null = All Photos
    val selectedAlbumId: StateFlow<String?> = _selectedAlbumId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPhotoIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPhotoIds: StateFlow<Set<String>> = _selectedPhotoIds.asStateFlow()

    private val _statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val statusMessage = _statusMessage.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val sortOrder: StateFlow<SortOrder> = preferences.sortOrder.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SortOrder.DATE_DESC
    )
    val themeMode: StateFlow<AppThemeMode> = preferences.themeMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), AppThemeMode.DARK
    )
    val autoLockPolicy: StateFlow<AutoLockPolicy> = preferences.autoLockPolicy.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoLockPolicy.IMMEDIATE
    )

    val albums: StateFlow<List<AlbumEntity>> = repository.albums.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val trashedPhotos: StateFlow<List<VaultPhotoEntity>> = repository.trashedPhotos.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val totalEncryptedBytes: StateFlow<Long> = repository.totalEncryptedBytes.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L
    )

    val filteredPhotos: StateFlow<List<VaultPhotoEntity>> = combine(
        repository.activePhotos,
        _selectedAlbumId,
        _searchQuery,
        sortOrder
    ) { photos, albumId, query, order ->
        val byAlbum = if (albumId == null) photos else photos.filter { it.albumId == albumId }
        val byQuery = if (query.isBlank()) byAlbum else {
            val q = query.trim().lowercase()
            byAlbum.filter { it.displayName.lowercase().contains(q) }
        }
        when (order) {
            SortOrder.DATE_DESC -> byQuery.sortedByDescending { it.createdAt }
            SortOrder.DATE_ASC -> byQuery.sortedBy { it.createdAt }
            SortOrder.NAME_ASC -> byQuery.sortedBy { it.displayName.lowercase() }
            SortOrder.NAME_DESC -> byQuery.sortedByDescending { it.displayName.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectAlbum(albumId: String?) {
        _selectedAlbumId.value = albumId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { preferences.setSortOrder(order) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setAutoLockPolicy(policy: AutoLockPolicy) {
        viewModelScope.launch { preferences.setAutoLockPolicy(policy) }
    }

    fun togglePhotoSelection(id: String) {
        _selectedPhotoIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun clearSelection() {
        _selectedPhotoIds.value = emptySet()
    }

    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.importPhotos(uris, _selectedAlbumId.value)
            _isBusy.value = false
            result.onSuccess { count ->
                _statusMessage.tryEmit("Encrypted and imported $count photo(s)")
            }.onFailure { err ->
                _statusMessage.tryEmit("Import failed: ${err.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    suspend fun loadDecryptedThumbnail(photo: VaultPhotoEntity): ByteArray? {
        return runCatching { repository.decryptThumbnail(photo) }.getOrNull()
    }

    suspend fun loadDecryptedFullPhoto(photo: VaultPhotoEntity): ByteArray? {
        return runCatching { repository.decryptFullPhoto(photo) }.getOrNull()
    }

    fun createAlbum(name: String) {
        viewModelScope.launch {
            val album = repository.createAlbum(name)
            _statusMessage.tryEmit("Created album \"${album.name}\"")
        }
    }

    fun renameAlbum(albumId: String, newName: String) {
        viewModelScope.launch {
            repository.renameAlbum(albumId, newName)
            _statusMessage.tryEmit("Album renamed")
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch {
            if (_selectedAlbumId.value == albumId) _selectedAlbumId.value = null
            repository.deleteAlbum(albumId)
            _statusMessage.tryEmit("Album deleted (photos moved to All Photos)")
        }
    }

    fun moveSelectedToAlbum(targetAlbumId: String?) {
        val ids = _selectedPhotoIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.movePhotosToAlbum(ids, targetAlbumId)
            clearSelection()
            _statusMessage.tryEmit("Moved ${ids.size} photo(s)")
        }
    }

    fun movePhotoToAlbum(photoId: String, targetAlbumId: String?) {
        viewModelScope.launch {
            repository.movePhotosToAlbum(listOf(photoId), targetAlbumId)
            _statusMessage.tryEmit("Photo moved")
        }
    }

    fun moveSelectedToTrash() {
        val ids = _selectedPhotoIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveToTrash(ids)
            clearSelection()
            _statusMessage.tryEmit("Moved ${ids.size} photo(s) to Trash")
        }
    }

    fun movePhotoToTrash(photoId: String) {
        viewModelScope.launch {
            repository.moveToTrash(listOf(photoId))
            _statusMessage.tryEmit("Moved photo to Trash")
        }
    }

    fun restoreFromTrash(photoIds: List<String>) {
        viewModelScope.launch {
            repository.restoreFromTrash(photoIds)
            _statusMessage.tryEmit("Restored ${photoIds.size} photo(s)")
        }
    }

    fun permanentlyDelete(photoIds: List<String>) {
        viewModelScope.launch {
            repository.permanentlyDelete(photoIds)
            _statusMessage.tryEmit("Securely wiped ${photoIds.size} photo(s)")
        }
    }

    fun verifyCurrentPin(pin: String): Boolean = pinManager.verifyPin(pin)

    fun changePin(currentPin: String, newPin: String): Result<Unit> {
        if (!pinManager.verifyPin(currentPin)) {
            return Result.failure(IllegalArgumentException("Current PIN is incorrect"))
        }
        return runCatching { pinManager.setPin(newPin) }
    }

    fun resetPin() {
        pinManager.clearPin()
    }

    fun exportBackup(destinationUri: Uri, passphrase: String) {
        if (passphrase.length < 6) {
            _statusMessage.tryEmit("Backup passphrase must be at least 6 characters")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.exportEncryptedBackup(destinationUri, passphrase.toCharArray())
            _isBusy.value = false
            result.onSuccess { count ->
                _statusMessage.tryEmit("Exported encrypted backup ($count photos)")
            }.onFailure { err ->
                _statusMessage.tryEmit("Backup export failed: ${err.localizedMessage}")
            }
        }
    }

    fun restoreBackup(sourceUri: Uri, pin: String, passphrase: String, replaceExisting: Boolean) {
        if (!pinManager.verifyPin(pin)) {
            _statusMessage.tryEmit("Authentication failed: invalid secret PIN")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.restoreEncryptedBackup(
                sourceUri,
                passphrase.toCharArray(),
                replaceExisting
            )
            _isBusy.value = false
            result.onSuccess { count ->
                _statusMessage.tryEmit("Restored $count encrypted photo(s) into vault")
            }.onFailure {
                _statusMessage.tryEmit("Restore failed: incorrect passphrase or corrupted archive")
            }
        }
    }

    companion object {
        fun factory(
            repository: VaultRepository,
            preferences: VaultPreferences,
            pinManager: PinManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VaultViewModel(repository, preferences, pinManager) as T
            }
        }
    }
}
