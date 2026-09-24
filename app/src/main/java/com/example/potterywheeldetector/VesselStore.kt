package com.example.potterywheeldetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class VesselFolder(val id: String, val name: String)
data class SavedVessel(
    val id: String,
    val folderId: String,
    val name: String,
    val createdAt: Long,
    val heightCm: Double?,
    val wallMm: Double,
    val allowancePercent: Double,
    val profile: VesselProfile
)

class VesselStore(context: Context) {
    private val directory = File(context.filesDir, "vessels").apply { mkdirs() }
    private val index = File(directory, "index.json")
    private val folders = mutableListOf<VesselFolder>()
    private val vessels = mutableListOf<SavedVessel>()

    init { load() }

    fun folders(): List<VesselFolder> = folders.toList()
    fun vessels(folderId: String): List<SavedVessel> =
        vessels.filter { it.folderId == folderId }.sortedByDescending { it.createdAt }
    fun find(id: String?): SavedVessel? = vessels.firstOrNull { it.id == id }

    fun createFolder(name: String): VesselFolder {
        val clean = name.trim()
        require(clean.isNotEmpty())
        require(folders.none { it.name.equals(clean, ignoreCase = true) })
        val folder = VesselFolder(UUID.randomUUID().toString(), clean)
        folders.add(folder)
        persist()
        return folder
    }

    fun renameFolder(id: String, name: String) {
        val clean = name.trim()
        require(clean.isNotEmpty())
        require(folders.none { it.id != id && it.name.equals(clean, ignoreCase = true) })
        val index = folders.indexOfFirst { it.id == id }
        require(index >= 0)
        folders[index] = folders[index].copy(name = clean)
        persist()
    }

    fun deleteFolder(id: String) {
        require(folders.size > 1)
        require(vessels.none { it.folderId == id })
        folders.removeAll { it.id == id }
        persist()
    }

    fun save(
        existingId: String?, folderId: String, name: String, bitmap: Bitmap,
        profile: VesselProfile, heightCm: Double?, wallMm: Double,
        allowancePercent: Double
    ): SavedVessel {
        require(folders.any { it.id == folderId })
        val clean = name.trim()
        require(clean.isNotEmpty())
        val id = existingId ?: UUID.randomUUID().toString()
        val old = vessels.firstOrNull { it.id == id }
        val image = File(directory, "$id.jpg")
        val imageFile = AtomicFile(image)
        val stream = imageFile.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream))
            imageFile.finishWrite(stream)
        } catch (error: Exception) {
            imageFile.failWrite(stream)
            throw error
        }
        val entry = SavedVessel(id, folderId, clean, old?.createdAt ?: System.currentTimeMillis(),
            heightCm, wallMm, allowancePercent, profile)
        vessels.removeAll { it.id == id }
        vessels.add(entry)
        persist()
        return entry
    }

    fun loadImage(id: String): Bitmap? = try {
        AtomicFile(File(directory, "$id.jpg")).openRead().use { BitmapFactory.decodeStream(it) }
    } catch (error: Exception) { null }

    fun loadThumbnail(id: String): Bitmap? = try {
        AtomicFile(File(directory, "$id.jpg")).openRead().use {
            BitmapFactory.decodeStream(it, null,
                BitmapFactory.Options().apply { inSampleSize = 8 })
        }
    } catch (error: Exception) { null }

    fun delete(id: String) {
        vessels.removeAll { it.id == id }
        persist()
        File(directory, "$id.jpg").delete()
    }

    private fun load() {
        if (!index.exists() && !File(index.path + ".bak").exists()) {
            folders.add(VesselFolder(UUID.randomUUID().toString(), "My vessels"))
            persist()
            return
        }
        try {
            val root = JSONObject(AtomicFile(index).openRead().bufferedReader().use { it.readText() })
            val folderArray = root.getJSONArray("folders")
            for (i in 0 until folderArray.length()) {
                val item = folderArray.getJSONObject(i)
                folders.add(VesselFolder(item.getString("id"), item.getString("name")))
            }
            val vesselArray = root.getJSONArray("vessels")
            for (i in 0 until vesselArray.length()) {
                val item = vesselArray.getJSONObject(i)
                val rowsJson = item.getJSONArray("rows")
                val rows = ArrayList<ProfileRow>(rowsJson.length())
                for (j in 0 until rowsJson.length()) {
                    val row = rowsJson.getJSONArray(j)
                    rows.add(ProfileRow(row.getDouble(0).toFloat(), row.getDouble(1).toFloat(),
                        row.getDouble(2).toFloat()))
                }
                if (rows.size < 2) continue
                vessels.add(SavedVessel(item.getString("id"), item.getString("folderId"),
                    item.getString("name"), item.getLong("createdAt"),
                    if (item.isNull("heightCm")) null else item.getDouble("heightCm"),
                    item.getDouble("wallMm"), item.getDouble("allowancePercent"),
                    VesselProfile(item.getInt("imageWidth"), item.getInt("imageHeight"), rows)))
            }
        } catch (error: Exception) {
            // Keep a damaged index on disk for recovery and present an empty library.
            folders.clear()
            vessels.clear()
            folders.add(VesselFolder(UUID.randomUUID().toString(), "My vessels"))
        }
        if (folders.isEmpty()) folders.add(VesselFolder(UUID.randomUUID().toString(), "My vessels"))
    }

    private fun persist() {
        val root = JSONObject()
        root.put("folders", JSONArray().apply {
            folders.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
        })
        root.put("vessels", JSONArray().apply {
            vessels.forEach { entry ->
                val rows = JSONArray()
                entry.profile.rows.forEach { row ->
                    rows.put(JSONArray().put(row.y.toDouble()).put(row.left.toDouble())
                        .put(row.right.toDouble()))
                }
                put(JSONObject().put("id", entry.id).put("folderId", entry.folderId)
                    .put("name", entry.name).put("createdAt", entry.createdAt)
                    .put("heightCm", entry.heightCm ?: JSONObject.NULL)
                    .put("wallMm", entry.wallMm).put("allowancePercent", entry.allowancePercent)
                    .put("imageWidth", entry.profile.imageWidth)
                    .put("imageHeight", entry.profile.imageHeight).put("rows", rows))
            }
        })
        val indexFile = AtomicFile(index)
        val stream = indexFile.startWrite()
        try {
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            indexFile.finishWrite(stream)
        } catch (error: Exception) {
            indexFile.failWrite(stream)
            throw error
        }
    }
}
