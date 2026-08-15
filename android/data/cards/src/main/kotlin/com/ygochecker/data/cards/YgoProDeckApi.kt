package com.ygochecker.data.cards

import com.ygochecker.core.model.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Same endpoint as the web app (`YgoApiService`):
 * `https://db.ygoprodeck.com/api/v7/cardinfo.php`
 */
@Singleton
class YgoProDeckClient @Inject constructor(
    private val http: OkHttpClient,
) {
    suspend fun fetchByIds(ids: Collection<Int>, language: String? = null): List<Card> =
        withContext(Dispatchers.IO) {
            val unique = ids.filter { it > 0 }.distinct()
            if (unique.isEmpty()) return@withContext emptyList()
            unique.chunked(CHUNK_SIZE).flatMap { chunk -> fetchChunk(chunk, language) }
        }

    /** Fuzzy name search (`fname`) — same as web `YgoApiService.searchCardsPage$`. */
    suspend fun searchByFname(query: String, language: String = "en", limit: Int = 50): List<Card> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val url = API_BASE.toHttpUrl().newBuilder()
                .addQueryParameter("fname", trimmed)
                .addQueryParameter("misc", "yes")
                .addQueryParameter("num", limit.toString())
                .addQueryParameter("offset", "0")
                .apply { if (language == "it") addQueryParameter("language", "it") }
                .build()
            execute(url)
        }

    suspend fun fetchByExactName(name: String, language: String = "en"): Card? =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@withContext null
            searchByFname(trimmed, language, limit = 20)
                .firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        }

    /**
     * Full catalog sync via paginated `num`/`offset` (web uses the same API base).
     */
    suspend fun fetchCatalogPage(offset: Int, pageSize: Int = PAGE_SIZE): List<Card> =
        withContext(Dispatchers.IO) {
            val url = API_BASE.toHttpUrl().newBuilder()
                .addQueryParameter("misc", "yes")
                .addQueryParameter("num", pageSize.toString())
                .addQueryParameter("offset", offset.toString())
                .build()
            execute(url)
        }

    fun download(url: String): ByteArray = openStream(url).use { it.readBytes() }

    /** Streaming download — avoids holding 30MB+ scripts.json in a single byte[]. */
    fun openStream(url: String): java.io.InputStream {
        val request = Request.Builder().url(url).get().build()
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} for $url")
        }
        val body = response.body ?: run {
            response.close()
            throw IOException("Empty body for $url")
        }
        return object : java.io.FilterInputStream(body.byteStream()) {
            override fun close() {
                super.close()
                response.close()
            }
        }
    }

    private fun fetchChunk(ids: List<Int>, language: String?): List<Card> {
        val url = API_BASE.toHttpUrl().newBuilder()
            .addQueryParameter("id", ids.joinToString(","))
            .addQueryParameter("misc", "yes")
            .apply { if (language == "it") addQueryParameter("language", "it") }
            .build()
        return execute(url)
    }

    private fun execute(url: okhttp3.HttpUrl): List<Card> {
        val request = Request.Builder().url(url).get().header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YGOPRODeck HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            return parseCardInfo(body)
        }
    }

    companion object {
        const val API_BASE = "https://db.ygoprodeck.com/api/v7/cardinfo.php"
        const val CHUNK_SIZE = 25
        const val PAGE_SIZE = 500
        const val EFFECT_SCRIPTS_URL =
            "https://raw.githubusercontent.com/Gabriele-Vantaggiato/ygo-card-checker/main/src/assets/data/effect-scripts/scripts.json"
        const val RELATED_URL =
            "https://raw.githubusercontent.com/Gabriele-Vantaggiato/ygo-card-checker/main/src/assets/data/card-knowledge/related.json"
        const val FORMAT_LEGALITY_URL =
            "https://raw.githubusercontent.com/Gabriele-Vantaggiato/ygo-card-checker/main/src/assets/data/card-knowledge/format-legality.json"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .callTimeout(360, TimeUnit.SECONDS)
            .build()
    }
}

internal fun parseCardInfo(json: String): List<Card> {
    val root = JSONObject(json)
    val data = root.optJSONArray("data") ?: return emptyList()
    return buildList(data.length()) {
        for (i in 0 until data.length()) {
            val row = data.optJSONObject(i) ?: continue
            val id = row.optInt("id", 0)
            if (id <= 0) continue
            add(
                Card(
                    id = id,
                    name = row.optString("name"),
                    type = row.optString("type"),
                    race = row.optString("race").ifBlank { null },
                    attribute = row.optString("attribute").ifBlank { null },
                    attack = row.optIntOrNull("atk"),
                    defense = row.optIntOrNull("def"),
                    level = row.optIntOrNull("level") ?: row.optIntOrNull("linkval"),
                    description = row.optString("desc"),
                    year = parsePrintYear(row),
                ),
            )
        }
    }
}

private fun parsePrintYear(row: JSONObject): Int? {
    val misc = row.optJSONArray("misc_info") ?: return null
    val first = misc.optJSONObject(0) ?: return null
    val date = first.optString("tcg_date").ifBlank { first.optString("ocg_date") }
    return date.take(4).toIntOrNull()?.takeIf { it in 1999..2100 }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}
