package com.habit.app.data.photos

import android.net.Uri
import com.habit.app.domain.model.DietPhoto
import java.io.File

const val MAX_DIET_PHOTO_BYTES: Long = 12L * 1024 * 1024
const val MAX_DIET_PHOTOS = 3

data class CameraPhotoTarget(
    val photo: DietPhoto,
    val contentUri: Uri,
)

class DietPhotoPathPolicy(private val root: File) {
    fun resolve(relativePath: String): File {
        require(relativePath.isNotBlank()) { "照片路径不能为空" }
        require(!File(relativePath).isAbsolute) { "照片路径必须是相对路径" }
        val canonicalRoot = root.canonicalFile
        val resolved = File(canonicalRoot, relativePath).canonicalFile
        require(resolved.path.startsWith(canonicalRoot.path + File.separator)) { "照片路径越界" }
        return resolved
    }
}

interface DietPhotoStore {
    suspend fun stage(uri: Uri): DietPhoto
    fun createCameraTarget(): CameraPhotoTarget
    suspend fun acceptCameraTarget(target: CameraPhotoTarget): DietPhoto
    suspend fun commit(photos: List<DietPhoto>): List<DietPhoto>
    suspend fun copy(relativePath: String): DietPhoto
    suspend fun discard(relativePath: String)
    suspend fun delete(relativePath: String)
    suspend fun removeOrphans(referencedPaths: Set<String>)
    fun file(relativePath: String): File
}
