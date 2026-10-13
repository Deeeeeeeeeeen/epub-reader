package app.reader

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var lib: Library
    private lateinit var list: ListView
    private var books = listOf<Book>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        lib = Library(this)
        list = ListView(this)
        setContentView(list)
        list.setOnItemClickListener { _, _, i, _ ->
            startActivity(Intent(this, ReaderActivity::class.java).putExtra("id", books[i].id))
        }
        list.setOnItemLongClickListener { _, _, i, _ ->
            AlertDialog.Builder(this).setMessage("Remove \"${books[i].title}\"?")
                .setPositiveButton("Remove") { _, _ -> lib.remove(books[i].id); refresh() }
                .setNegativeButton("Cancel", null).show()
            true
        }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        books = lib.books()
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, books.map { it.title })
    }

    override fun onCreateOptionsMenu(m: Menu): Boolean {
        m.add("Add").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(i: MenuItem): Boolean {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/epub+zip", "application/octet-stream")), 1)
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        val uri = data?.data ?: return
        Thread {
            val ok = try { lib.import(uri); true } catch (e: Exception) { false }
            runOnUiThread {
                if (ok) refresh() else Toast.makeText(this, "Not a valid EPUB", Toast.LENGTH_LONG).show()
            }
        }.start()
    }
}
