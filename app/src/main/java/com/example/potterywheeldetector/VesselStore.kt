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
    val profile: VesselProfile,
    val baseMm: Double = wallMm,
    val handleOptions: HandleOptions = HandleOptions(),
    val category: VesselCategory = VesselCategory.suggest(profile, handleOptions.enabled)
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
    fun recent(limit: Int): List<SavedVessel> =
        vessels.sortedByDescending { it.createdAt }.take(limit)

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
        allowancePercent: Double, baseMm: Double, handleOptions: HandleOptions,
        category: VesselCategory
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
            heightCm, wallMm, allowancePercent, profile, baseMm, handleOptions, category)
        vessels.removeAll { it.id == id }
        vessels.add(entry)
        persist()
        return entry
    }

    fun loadImage(id: String): Bitmap? = try {
        AtomicFile(File(directory, "$id.jpg")).openRead().use { BitmapFactory.decodeStream(it) }
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
                val wallMm = item.getDouble("wallMm")
                val profile = VesselProfile(item.getInt("imageWidth"), item.getInt("imageHeight"),
                    rows, item.optJSONObject("handle")?.let(::readHandle))
                val handleOptions = item.optJSONObject("handleOptions")?.let(::readHandleOptions)
                    ?: HandleOptions()
                vessels.add(SavedVessel(item.getString("id"), item.getString("folderId"),
                    item.getString("name"), item.getLong("createdAt"),
                    if (item.isNull("heightCm")) null else item.getDouble("heightCm"),
                    wallMm, item.getDouble("allowancePercent"), profile,
                    // Vessels saved before base, handle and category existed.
                    item.optDouble("baseMm", wallMm), handleOptions,
                    VesselCategory.fromName(item.optString("category"))
                        ?: VesselCategory.suggest(profile, handleOptions.enabled)))
            }
        } catch (error: Exception) {
            // Keep a damaged index on disk for recovery and present an empty library.
            folders.clear()
            vessels.clear()
            folders.add(VesselFolder(UUID.randomUUID().toString(), "My vessels"))
        }
        if (folders.isEmpty()) folders.add(VesselFolder(UUID.randomUUID().toString(), "My vessels"))
    }

    private fun writeHandle(handle: VesselHandle) = JSONObject()
        .put("side", handle.side.name)
        .put("upperJoinY", handle.upperJoinY.toDouble())
        .put("lowerJoinY", handle.lowerJoinY.toDouble())
        .put("projection", handle.projection.toDouble())
        .put("hasEye", handle.hasEye)
        .put("path", JSONArray().apply {
            handle.path.forEach { point ->
                put(JSONArray().put(point.x.toDouble()).put(point.y.toDouble())
                    .put(point.thickness.toDouble()))
            }
        })

    /** A damaged or partial handle record is dropped rather than failing the library. */
    private fun readHandle(item: JSONObject): VesselHandle? = try {
        val pathJson = item.getJSONArray("path")
        val path = (0 until pathJson.length()).map { index ->
            val point = pathJson.getJSONArray(index)
            HandlePoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat(),
                point.getDouble(2).toFloat())
        }
        if (path.size < 2) null else VesselHandle(
            HandleSide.valueOf(item.getString("side")), path,
            item.getDouble("upperJoinY").toFloat(), item.getDouble("lowerJoinY").toFloat(),
            item.getDouble("projection").toFloat(), item.getBoolean("hasEye"))
    } catch (error: Exception) { null }

    private fun writeHandleOptions(options: HandleOptions) = JSONObject()
        .put("enabled", options.enabled)
        .put("section", options.section.name)
        .put("widthMm", options.widthMm)

    private fun readHandleOptions(item: JSONObject): HandleOptions {
        val fallback = HandleOptions()
        return HandleOptions(
            item.optBoolean("enabled", fallback.enabled),
            HandleSection.entries.firstOrNull { it.name == item.optString("section") }
                ?: fallback.section,
            item.optDouble("widthMm", fallback.widthMm).coerceIn(HandleOptions.WIDTH_RANGE)
        )
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
                    .put("imageHeight", entry.profile.imageHeight).put("rows", rows)
                    .put("baseMm", entry.baseMm)
                    .put("category", entry.category.name)
                    .put("handleOptions", writeHandleOptions(entry.handleOptions))
                    .put("handle", entry.profile.handle?.let(::writeHandle) ?: JSONObject.NULL))
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
