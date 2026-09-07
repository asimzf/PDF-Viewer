package com.asimzf.asimpdf.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** A document the user opened before, so it is one tap away next time. */
data class RecentDocument(
    val uri: String,
    val name: String,
    val lastOpened: Long,
    val pageCount: Int,
    val sizeBytes: Long
)

/**
 * The recent list, kept in shared preferences. Nothing leaves the device and the
 * list only ever holds URIs the user picked themselves.
 */
class RecentFiles(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<RecentDocument> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                RecentDocument(
                    uri = item.optString("uri"),
                    name = item.optString("name", "document.pdf"),
                    lastOpened = item.optLong("lastOpened"),
                    pageCount = item.optInt("pageCount"),
                    sizeBytes = item.optLong("sizeBytes")
                )
            }.filter { it.uri.isNotEmpty() }
        }.getOrDefault(emptyList()).sortedByDescending { it.lastOpened }
    }

    fun remember(uri: Uri, name: String, pageCount: Int, sizeBytes: Long) {
        val entry = RecentDocument(uri.toString(), name, System.currentTimeMillis(), pageCount, sizeBytes)
        val updated = (listOf(entry) + all().filterNot { it.uri == entry.uri }).take(MAX_ITEMS)
        persist(updated)
    }

    fun forget(uri: String) = persist(all().filterNot { it.uri == uri })

    fun clear() = persist(emptyList())

    /** Keeps read access to a picked document across app restarts. */
    fun persistPermission(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private fun persist(items: List<RecentDocument>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("uri", item.uri)
                    put("name", item.name)
                    put("lastOpened", item.lastOpened)
                    put("pageCount", item.pageCount)
                    put("sizeBytes", item.sizeBytes)
                }
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object {
        const val PREFS = "asimpdf_prefs"
        const val KEY_ITEMS = "recent_documents"
        const val MAX_ITEMS = 24
    }
}
