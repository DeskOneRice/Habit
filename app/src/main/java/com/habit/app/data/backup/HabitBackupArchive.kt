package com.habit.app.data.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ArchiveReadResult(
    val backup: HabitBackup,
    val photos: Map<String, File>,
    val skippedPhotoCount: Int,
)

object HabitBackupArchive {
    private const val METADATA_ENTRY = "backup.json"
    private const val PHOTO_PREFIX = "photos/"
    private const val MAX_PHOTO_BYTES = 12L * 1024 * 1024

    fun write(
        output: OutputStream,
        backup: HabitBackup,
        openPhoto: (String) -> InputStream?,
    ) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(METADATA_ENTRY))
            zip.write(HabitBackupCodec.encode(backup).encodeToByteArray())
            zip.closeEntry()
            backup.dietPhotos.map(BackupDietPhoto::relativePath).distinct().forEach { path ->
                requireSafeRelativePath(path)
                openPhoto(path)?.use { input ->
                    zip.putNextEntry(ZipEntry(PHOTO_PREFIX + path))
                    copyLimited(input, zip, MAX_PHOTO_BYTES)
                    zip.closeEntry()
                }
            }
        }
    }

    fun read(input: InputStream, targetRoot: File): ArchiveReadResult {
        targetRoot.mkdirs()
        val canonicalRoot = targetRoot.canonicalFile
        val names = mutableSetOf<String>()
        val photos = mutableMapOf<String, File>()
        var metadata: String? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(names.add(entry.name)) { "备份中存在重复条目" }
                if (entry.name == METADATA_ENTRY) {
                    metadata = zip.readBytesLimited(4L * 1024 * 1024).decodeToString()
                } else if (entry.name.startsWith(PHOTO_PREFIX)) {
                    val relativePath = entry.name.removePrefix(PHOTO_PREFIX)
                    requireSafeRelativePath(relativePath)
                    val file = File(canonicalRoot, relativePath).canonicalFile
                    require(file.path.startsWith(canonicalRoot.path + File.separator)) { "备份照片路径越界" }
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output -> copyLimited(zip, output, MAX_PHOTO_BYTES) }
                    photos[relativePath] = file
                } else {
                    throw InvalidBackupException("备份中包含未知条目：${entry.name}")
                }
                zip.closeEntry()
            }
        }
        val backup = HabitBackupCodec.decode(metadata ?: throw InvalidBackupException("备份缺少 backup.json"))
        val referenced = backup.dietPhotos.mapTo(mutableSetOf(), BackupDietPhoto::relativePath)
        photos.keys.filter { it !in referenced }.forEach { extra -> photos.remove(extra)?.delete() }
        return ArchiveReadResult(backup, photos, referenced.count { it !in photos })
    }

    private fun requireSafeRelativePath(path: String) {
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\') || path.contains('\\') ||
            path.split('/').any { it == ".." || it.isBlank() }
        ) throw InvalidBackupException("备份照片路径无效")
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw InvalidBackupException("备份照片超过 12 MB")
            output.write(buffer, 0, count)
        }
    }

    private fun InputStream.readBytesLimited(limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        copyLimited(this, output, limit)
        return output.toByteArray()
    }
}
