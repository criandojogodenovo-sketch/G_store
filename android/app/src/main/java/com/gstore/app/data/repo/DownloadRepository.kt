package com.gstore.app.data.repo

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.gstore.app.BuildConfig

/**
 * Fluxo de download de APKs.
 *
 * A API redireciona (302) para o asset do GitHub Releases — o DownloadManager
 * do sistema segue o redirect e baixa com notificação/progresso nativo.
 * O usuário final só vê "G Store baixando o jogo".
 */
class DownloadRepository(private val context: Context) {

    /** Inicia o download do APK do jogo e retorna o id do DownloadManager. */
    fun startDownload(slug: String, name: String, version: String?): Long {
        val url = "${BuildConfig.API_BASE_URL.trimEnd('/')}/api/games/$slug/download"
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("$name${version?.let { " $it" } ?: ""}")
            setDescription("Download do APK via G Store")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GStore/$slug.apk")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    data class DownloadStatus(val status: Int, val progress: Int)

    fun queryStatus(downloadId: Long): DownloadStatus {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        manager.query(query).use { cursor ->
            if (cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_FAILED
                val downloaded = if (downloadedIdx >= 0) cursor.getLong(downloadedIdx) else 0L
                val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else 0L
                val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                return DownloadStatus(status, progress)
            }
        }
        return DownloadStatus(DownloadManager.STATUS_FAILED, 0)
    }

    companion object {
        const val STATUS_RUNNING = DownloadManager.STATUS_RUNNING
        const val STATUS_SUCCESSFUL = DownloadManager.STATUS_SUCCESSFUL
        const val STATUS_FAILED = DownloadManager.STATUS_FAILED
    }
}
