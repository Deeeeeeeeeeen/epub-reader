package app.reader

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

data class Book(val id: String, val title: String)

class Library(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "books").apply { mkdirs() }
    private val prefs = ctx.getSharedPreferences("lib", Context.MODE_PRIVATE)

    fun file(id: String) = File(dir, "$id.epub")

    fun books() = dir.listFiles().orEmpty().sortedByDescending { it.lastModified() }.map {
        val id = it.nameWithoutExtension
        Book(id, prefs.getString("t:$id", id)!!)
    }

    fun import(uri: Uri) {
        val id = UUID.randomUUID().toString()
        val f = file(id)
        try {
            ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
            val title = Epub(f).use { it.title }
            prefs.edit().putString("t:$id", title).apply()
        } catch (e: Exception) {
            f.delete()
            throw e
        }
    }

    fun remove(id: String) {
        file(id).delete()
        prefs.edit().remove("t:$id").remove("i:$id").remove("f:$id").apply()
    }

    fun position(id: String) = prefs.getInt("i:$id", 0) to prefs.getFloat("f:$id", 0f)

    fun savePosition(id: String, index: Int, frac: Float) {
        prefs.edit().putInt("i:$id", index).putFloat("f:$id", frac).apply()
    }

    var zoom: Int
        get() = prefs.getInt("zoom", 100)
        set(v) { prefs.edit().putInt("zoom", v).apply() }
}
