package com.gstore.app.screens.library

import android.app.DownloadManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gstore.app.data.remote.GameDto
import com.gstore.app.data.repo.GStoreRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LibraryItem(
    val downloadId: Long,
    val game: GameDto?,
    val title: String,
    val status: Int,
    val progress: Int,
)

class LibraryViewModel(private val repository: GStoreRepository) : ViewModel() {

    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    val items: StateFlow<List<LibraryItem>> = _items

    fun observe(context: Context) {
        viewModelScope.launch {
            while (true) {
                _items.value = queryDownloads(context)
                delay(1500)
            }
        }
    }

    private fun queryDownloads(context: Context): List<LibraryItem> {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_RUNNING or
                DownloadManager.STATUS_SUCCESSFUL or
                DownloadManager.STATUS_PAUSED or
                DownloadManager.STATUS_PENDING,
        )
        val out = mutableListOf<LibraryItem>()
        manager.query(query).use { cursor ->
            while (cursor.moveToNext()) {
                val idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                if (idIdx < 0) continue
                val id = cursor.getLong(idIdx)
                val title = if (titleIdx >= 0) cursor.getString(titleIdx) ?: "Download" else "Download"
                val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else 0
                val downloaded = if (bytesIdx >= 0) cursor.getLong(bytesIdx) else 0L
                val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else 0L
                val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                // Downloads da G Store têm título "Nome vX"; demais são ignorados.
                if (title.contains("v") || status == DownloadManager.STATUS_SUCCESSFUL) {
                    out.add(LibraryItem(id, null, title, status, progress))
                }
            }
        }
        return out
    }

    companion object {
        fun factory(repository: GStoreRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                LibraryViewModel(repository) as T
        }
    }
}
