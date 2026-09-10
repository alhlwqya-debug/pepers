package com.add.pepers.cloud

import android.net.Uri

/**
 * عقد مزامنة مستقل عن واجهة التطبيق.
 * يمكن ربطه لاحقًا بخادم REST/Firebase أو Google Drive دون تغيير شاشات التسجيل.
 */
interface CloudSyncProvider {
    val providerName: String

    suspend fun uploadBackup(uri: Uri): Result<String>

    suspend fun downloadLatestBackup(): Result<Uri>

    suspend fun listBackups(): Result<List<CloudBackupItem>>
}

data class CloudBackupItem(
    val id: String,
    val name: String,
    val createdAt: Long,
    val sizeBytes: Long
)

data class SyncStatus(
    val connected: Boolean,
    val lastSyncAt: Long? = null,
    val message: String = ""
)

object CloudSyncRegistry {
    @Volatile
    private var provider: CloudSyncProvider? = null

    fun setProvider(value: CloudSyncProvider?) {
        provider = value
    }

    fun current(): CloudSyncProvider? = provider
}
