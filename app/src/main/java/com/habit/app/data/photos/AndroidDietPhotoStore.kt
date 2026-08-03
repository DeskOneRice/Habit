package com.habit.app.data.photos

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.habit.app.domain.model.DietPhoto
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidDietPhotoStore(private val context: Context) : DietPhotoStore {
    private val root = File(context.filesDir, "diet_photos").apply { mkdirs() }
    private val staging = File(root, "staging").apply { mkdirs() }
    private val library = File(root, "library").apply { mkdirs() }
    private val policy = DietPhotoPathPolicy(root)

    override suspend fun stage(uri: Uri): DietPhoto = withContext(Dispatchers.IO) {
        val target = File(staging, "${UUID.randomUUID()}.jpg")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选照片" }
                FileOutputStream(target).use { output -> copyLimited(input, output) }
            }
            validateImage(target)
            DietPhoto(relativePath = relative(target), sortOrder = 0)
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    override fun createCameraTarget(): CameraPhotoTarget {
        val file = File(staging, "${UUID.randomUUID()}.jpg")
        file.parentFile?.mkdirs()
        val photo = DietPhoto(relativePath = relative(file), sortOrder = 0)
        return CameraPhotoTarget(
            photo = photo,
            contentUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file),
        )
    }

    override suspend fun acceptCameraTarget(target: CameraPhotoTarget): DietPhoto = withContext(Dispatchers.IO) {
        val file = policy.resolve(target.photo.relativePath)
        require(file.length() in 1..MAX_DIET_PHOTO_BYTES) { "照片不能超过 12 MB" }
        validateImage(file)
        target.photo
    }

    override suspend fun commit(photos: List<DietPhoto>): List<DietPhoto> = withContext(Dispatchers.IO) {
        photos.mapIndexed { index, photo ->
            val source = policy.resolve(photo.relativePath)
            if (source.parentFile?.canonicalFile == staging.canonicalFile) {
                val target = File(library, "${UUID.randomUUID()}.jpg")
                require(source.renameTo(target) || copyThenDelete(source, target)) { "照片保存失败" }
                DietPhoto(relativePath = relative(target), sortOrder = index)
            } else {
                photo.copy(sortOrder = index)
            }
        }
    }

    override suspend fun copy(relativePath: String): DietPhoto = withContext(Dispatchers.IO) {
        val source = policy.resolve(relativePath)
        require(source.isFile) { "照片不存在" }
        val target = File(library, "${UUID.randomUUID()}.jpg")
        source.inputStream().use { input -> FileOutputStream(target).use { output -> copyLimited(input, output) } }
        validateImage(target)
        DietPhoto(relativePath = relative(target), sortOrder = 0)
    }

    override suspend fun discard(relativePath: String) = withContext(Dispatchers.IO) {
        val file = policy.resolve(relativePath)
        if (file.parentFile?.canonicalFile == staging.canonicalFile) file.delete()
        Unit
    }

    override suspend fun delete(relativePath: String) = withContext(Dispatchers.IO) {
        policy.resolve(relativePath).delete()
        Unit
    }

    override suspend fun removeOrphans(referencedPaths: Set<String>) = withContext(Dispatchers.IO) {
        library.listFiles().orEmpty().filter(File::isFile).forEach { file ->
            if (relative(file) !in referencedPaths) file.delete()
        }
        staging.listFiles().orEmpty().filter(File::isFile).forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000L) file.delete()
        }
    }

    override fun file(relativePath: String): File = policy.resolve(relativePath)

    private fun relative(file: File): String = file.canonicalFile.relativeTo(root.canonicalFile).invariantSeparatorsPath

    private fun validateImage(file: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "文件不是有效图片" }
    }

    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_DIET_PHOTO_BYTES) { "照片不能超过 12 MB" }
            output.write(buffer, 0, count)
        }
    }

    private fun copyThenDelete(source: File, target: File): Boolean = try {
        source.inputStream().use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        source.delete()
        true
    } catch (_: Exception) {
        target.delete()
        false
    }
}
