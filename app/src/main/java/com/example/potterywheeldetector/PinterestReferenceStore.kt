package com.example.potterywheeldetector

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PinterestReference(val id: String, val url: String, val name: String)

class PinterestReferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("pinterest_references", Context.MODE_PRIVATE)

    fun all(): List<PinterestReference> = try {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            PinterestReference(item.getString("id"), item.getString("url"), item.getString("name"))
        }
    } catch (_: Exception) { emptyList() }

    fun add(urlText: String, nameText: String) {
        val url = pinterestUrl(urlText) ?: throw IllegalArgumentException("Enter a Pinterest link")
        val name = nameText.trim().ifEmpty { "Pinterest reference" }
        val items = all().toMutableList()
        if (items.any { it.url == url }) throw IllegalArgumentException("Link already saved")
        items.add(0, PinterestReference(UUID.randomUUID().toString(), url, name))
        save(items)
    }

    fun delete(id: String) = save(all().filterNot { it.id == id })

    private fun save(items: List<PinterestReference>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("url", it.url)
            .put("name", it.name)) }
        check(prefs.edit().putString("items", array.toString()).commit())
    }

    companion object {
        fun pinterestUrl(text: String): String? {
            val candidate = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
                .find(text)?.value?.trimEnd('.', ',', ')') ?: return null
            val uri = Uri.parse(candidate)
            val host = uri.host?.lowercase() ?: return null
            if (uri.scheme != "https" ||
                !(host == "pin.it" || host == "pinterest.com" || host.endsWith(".pinterest.com")))
                return null
            return uri.toString()
        }
    }
}
