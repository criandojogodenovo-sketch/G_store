package com.gstore.app.data.repo

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/**
 * Fluxo de download de APKs.
 *
 * As URLs dos APKs vivem na tabela `game_versions` (criadas pelo admin
 * na área de administração) e apontam para o GitHub Releases — o
 * DownloadManager do sistema baixa direto do GitHub, sem nenhum
 * intermediário nem token (o repositório é público).
 */
class DownloadRepository(private val context: Context) {

    /** Inicia o download do APK do jogo e retorna o id do DownloadManager. */
    fun startDownload(slug: String, name: String, version: GameVersionDto): Long {
        val request = DownloadManager.Request(Uri.parse(version.apkUrl)).apply {
            setTitle("$name ${version.version}")
            setDescription("Download do APK via G Store")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GStore/$slug-${version.version}.apk")
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
