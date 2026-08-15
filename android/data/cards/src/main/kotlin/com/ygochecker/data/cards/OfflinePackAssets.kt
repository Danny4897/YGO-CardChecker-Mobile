package com.ygochecker.data.cards

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.GZIPInputStream

internal object OfflinePackAssets {
    const val CARDS_HAT = "offline-pack/cards-hat.json.gz"
    const val SCRIPTS_HAT = "offline-pack/scripts-hat.json.gz"
    const val RELATED_HAT = "offline-pack/related-hat.json.gz"
    const val SYNERGIES = "offline-pack/synergies-manual.json.gz"
    const val PACK_VERSION = "hat-offline-v3-scripts-no-lua"

    private const val TAG = "OfflinePackAssets"

    /**
     * Opens a pack asset. Source files are `*.json.gz`, but AAPT auto-decompresses `.gz`
     * under `assets/` and packages them as `*.json` — so we try gzip path first, then plain.
     */
    fun openAsset(context: Context, gzipAssetPath: String): InputStream {
        try {
            val raw = BufferedInputStream(context.assets.open(gzipAssetPath))
            return GZIPInputStream(raw)
        } catch (_: FileNotFoundException) {
            val plain = gzipAssetPath.removeSuffix(".gz")
            Log.i(TAG, "Using AAPT-decompressed asset $plain")
            return BufferedInputStream(context.assets.open(plain))
        }
    }

    fun readAssetText(context: Context, gzipAssetPath: String): String =
        openAsset(context, gzipAssetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** @deprecated use [openAsset] */
    fun openGzip(context: Context, assetPath: String): InputStream = openAsset(context, assetPath)

    /** @deprecated use [readAssetText] */
    fun readGzipText(context: Context, assetPath: String): String = readAssetText(context, assetPath)

    fun parseCards(json: String): List<CardEntity> {
        val array = JSONArray(json)
        val out = ArrayList<CardEntity>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optInt("id")
            if (id <= 0) continue
            out += CardEntity(
                id = id,
                name = o.optString("name"),
                type = o.optString("type"),
                race = o.optString("race").ifBlank { null },
                attribute = o.optString("attribute").ifBlank { null },
                attack = o.optIntOrNull("attack"),
                defense = o.optIntOrNull("defense"),
                level = o.optIntOrNull("level"),
                description = o.optString("description"),
                year = o.optIntOrNull("year"),
            )
        }
        return out
    }

    fun parseManualSynergies(json: String): List<CardRelationEntity> {
        val root = JSONObject(json)
        val entries = root.optJSONObject("entries") ?: return emptyList()
        val out = ArrayList<CardRelationEntity>()
        val keys = entries.keys()
        while (keys.hasNext()) {
            val sourceKey = keys.next()
            val sourceId = sourceKey.toIntOrNull() ?: continue
            val arr = entries.optJSONArray(sourceKey) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val targetId = o.optInt("id")
                if (targetId <= 0) continue
                out += CardRelationEntity(
                    sourceId = sourceId,
                    targetId = targetId,
                    relation = o.optString("relation").ifBlank { "manual_synergy" },
                    score = o.optDouble("score", 1.5),
                    targetName = o.optString("name"),
                )
            }
        }
        return out
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}
