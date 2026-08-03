package com.habit.app.data.backup

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.habit.app.data.preferences.BackupFolder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BACKUP_MIME_TYPE = "application/json"
private const val DEFAULT_RELATIVE_PATH = "Download/Habit/数据备份/"

class AndroidBackupDocumentStore(context: Context) : BackupDocumentStore {
    private val applicationContext = context.applicationContext
    private val resolver: ContentResolver = applicationContext.contentResolver

    override suspend fun listManagedBackups(folder: BackupFolder): List<String> = withContext(Dispatchers.IO) {
        when (folder) {
            BackupFolder.Default -> listDefault()
            is BackupFolder.Tree -> tree(folder).listFiles().mapNotNull(DocumentFile::getName)
        }.filter(::isManagedBackupName).sorted()
    }

    override suspend fun read(folder: BackupFolder, name: String): ByteArray = withContext(Dispatchers.IO) {
        when (folder) {
            BackupFolder.Default -> if (Build.VERSION.SDK_INT >= 29) {
                resolver.openInputStream(requireNotNull(findDefaultUri(name)))?.use { it.readBytes() }
                    ?: error("无法读取备份：$name")
            } else {
                defaultFile(name).readBytes()
            }
            is BackupFolder.Tree -> {
                val document = tree(folder).findFile(name) ?: error("找不到备份：$name")
                resolver.openInputStream(document.uri)?.use { it.readBytes() }
                    ?: error("无法读取备份：$name")
            }
        }
    }

    override suspend fun write(folder: BackupFolder, name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        require(isManagedBackupName(name)) { "无效的备份文件名" }
        when (folder) {
            BackupFolder.Default -> writeDefault(name, bytes)
            is BackupFolder.Tree -> {
                val directory = tree(folder)
                directory.findFile(name)?.delete()
                val document = directory.createFile(BACKUP_MIME_TYPE, name)
                    ?: error("无法在所选目录创建备份")
                resolver.openOutputStream(document.uri, "wt")?.use { it.write(bytes) }
                    ?: error("无法写入备份：$name")
            }
        }
    }

    override suspend fun writeStream(
        folder: BackupFolder,
        name: String,
        mimeType: String,
        writer: (java.io.OutputStream) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(isManagedBackupName(name)) { "无效的备份文件名" }
        when (folder) {
            BackupFolder.Default -> writeDefaultStream(name, mimeType, writer)
            is BackupFolder.Tree -> {
                val directory = tree(folder)
                directory.findFile(name)?.delete()
                val document = directory.createFile(mimeType, name) ?: error("无法在所选目录创建备份")
                resolver.openOutputStream(document.uri, "wt")?.use(writer) ?: error("无法写入备份：$name")
            }
        }
    }

    override suspend fun delete(folder: BackupFolder, name: String) = withContext(Dispatchers.IO) {
        when (folder) {
            BackupFolder.Default -> if (Build.VERSION.SDK_INT >= 29) {
                findDefaultUri(name)?.let { uri -> resolver.delete(uri, null, null) }
            } else {
                defaultFile(name).delete()
            }
            is BackupFolder.Tree -> tree(folder).findFile(name)?.delete()
        }
        Unit
    }

    private fun tree(folder: BackupFolder.Tree): DocumentFile =
        DocumentFile.fromTreeUri(applicationContext, Uri.parse(folder.uri))
            ?: error("备份目录授权已失效")

    private fun listDefault(): List<String> = if (Build.VERSION.SDK_INT >= 29) {
        val names = mutableListOf<String>()
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(DEFAULT_RELATIVE_PATH),
            "${MediaStore.MediaColumns.DATE_ADDED} ASC",
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) names += cursor.getString(nameIndex)
        }
        names
    } else {
        defaultDirectory().listFiles()?.map(File::getName).orEmpty()
    }

    private fun writeDefault(name: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= 29) {
            findDefaultUri(name)?.let { uri -> resolver.delete(uri, null, null) }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, BACKUP_MIME_TYPE)
                put(MediaStore.MediaColumns.RELATIVE_PATH, DEFAULT_RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建默认备份文件")
            try {
                resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    ?: error("无法写入默认备份文件")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            val file = defaultFile(name)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }

    private fun writeDefaultStream(name: String, mimeType: String, writer: (java.io.OutputStream) -> Unit) {
        if (Build.VERSION.SDK_INT >= 29) {
            findDefaultUri(name)?.let { uri -> resolver.delete(uri, null, null) }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, DEFAULT_RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建默认备份文件")
            try {
                resolver.openOutputStream(uri, "wt")?.use(writer) ?: error("无法写入默认备份文件")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            val file = defaultFile(name)
            file.parentFile?.mkdirs()
            file.outputStream().use(writer)
        }
    }

    private fun findDefaultUri(name: String): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(DEFAULT_RELATIVE_PATH, name),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)),
                )
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun defaultDirectory(): File = Environment
        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        .resolve("Habit")
        .resolve("数据备份")

    private fun defaultFile(name: String): File = defaultDirectory().resolve(name)
}
