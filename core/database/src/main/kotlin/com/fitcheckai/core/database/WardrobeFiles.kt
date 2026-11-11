package com.fitcheckai.core.database

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

/** Repository owns only direct children of wardrobe/. Drafts belong to core:imaging. */
class WardrobeFiles @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.filesDir, "wardrobe")

    fun importImage(id: String, sourcePath: String): Pair<String, String> {
        require(id.matches(Regex("[A-Za-z0-9-]+")))
        val source = File(sourcePath)
        require(source.isFile && source.length() > 0) { "Ảnh không còn tồn tại. Hãy chọn lại ảnh." }
        check(directory.mkdirs() || directory.isDirectory) { "Không thể tạo thư mục ảnh." }
        val destination = File(directory, "$id.png")
        val pending = File(directory, "$id.tmp")
        try {
            source.inputStream().use { input -> pending.outputStream().use { input.copyTo(it) } }
            check(pending.renameTo(destination)) { "Không thể lưu ảnh." }
            val digest = MessageDigest.getInstance("SHA-256")
            destination.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var size = input.read(buffer)
                while (size != -1) {
                    digest.update(buffer, 0, size)
                    size = input.read(buffer)
                }
            }
            return destination.absolutePath to digest.digest().joinToString("") { "%02x".format(it) }
        } catch (error: Exception) {
            pending.delete()
            destination.delete()
            throw error
        }
    }

    fun deleteOwned(path: String) {
        val file = File(path).canonicalFile
        if (file.parentFile == directory.canonicalFile && file.extension == "png" && file.exists()) {
            if (!file.delete()) throw IOException("Không thể xóa ảnh.")
        }
    }
}
