package com.ygochecker.data.cards

import android.content.Context
import android.util.JsonReader
import com.ygochecker.core.common.AppError
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.domain.OfflinePackRepository
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.CatalogSyncProgress
import com.ygochecker.core.model.EffectMechanicTags
import com.ygochecker.core.model.EffectScriptSummary
import com.ygochecker.core.model.EffectTextProfiler
import com.ygochecker.core.model.GameFormat
import com.ygochecker.core.model.OfflinePackStatus
import com.ygochecker.core.model.RelatedCardRef
import com.ygochecker.core.model.SyncPhase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomOfflinePackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CardDao,
    private val api: YgoProDeckClient,
    private val flowCatalog: AssetFlowCatalog,
) : OfflinePackRepository {
    private val progress = MutableStateFlow<CatalogSyncProgress?>(null)
    private val statusTick = MutableStateFlow(0)
    private val limitCache = ConcurrentHashMap<String, Int>()
    /** Card ids present in the legality pack for at least one format. */
    private val knownLimitCards = ConcurrentHashMap.newKeySet<Int>()

    override fun status(): Flow<OfflinePackStatus> = flow {
        emit(readStatus())
        statusTick.collect { emit(readStatus()) }
    }.flowOn(Dispatchers.IO)

    override fun progress(): Flow<CatalogSyncProgress?> = progress

    override fun clearProgress() {
        progress.value = null
    }

    private suspend fun readStatus() = OfflinePackStatus(
        catalogCount = dao.count(),
        legalityReady = dao.formatLimitCount() > 0,
        effectScriptCount = dao.effectScriptCount(),
        lastCatalogSyncAt = dao.meta(META_CATALOG)?.toLongOrNull(),
        lastScriptsSyncAt = dao.meta(META_SCRIPTS)?.toLongOrNull(),
    )

    override fun maxCopiesOrNull(cardId: Int, format: GameFormat): Int? {
        // Pack not loaded → caller may stub. Once loaded, missing format row = not playable.
        if (limitCache.isEmpty()) return null
        return limitCache["$cardId:${format.id}"] ?: 0
    }

    override suspend fun effectScriptCount(): Int = dao.effectScriptCount()

    override suspend fun effectScript(cardId: Int): EffectScriptSummary? =
        dao.effectScript(cardId)?.toSummary()

    override suspend fun effectScripts(ids: Collection<Int>): List<EffectScriptSummary> {
        val unique = ids.filter { it > 0 }.distinct()
        if (unique.isEmpty()) return emptyList()
        return dao.effectScripts(unique).map(EffectScriptEntity::toSummary)
    }

    override suspend fun relatedCards(cardId: Int, limit: Int): List<RelatedCardRef> =
        withContext(Dispatchers.IO) {
            val want = limit.coerceAtLeast(1)
            val edges = runCatching {
                dao.relatedFor(cardId, (want * 3).coerceAtLeast(36)).map {
                    RelatedCardRef(
                        id = it.targetId,
                        name = it.targetName,
                        relation = it.relation,
                        score = it.score,
                    )
                }
            }.getOrDefault(emptyList())
            val source = dao.getByIds(listOf(cardId)).firstOrNull()?.let { entity ->
                Card(
                    id = entity.id,
                    name = entity.name,
                    type = entity.type,
                    race = entity.race,
                    attribute = entity.attribute,
                    attack = entity.attack,
                    defense = entity.defense,
                    level = entity.level,
                    description = entity.description,
                )
            }
            val script = dao.effectScript(cardId)?.toSummary()
            val enriched = runCatching {
                SynergyEnrichment.enrich(cardId, source, script, dao, want * 2)
            }.getOrElse {
                android.util.Log.w("OfflinePack", "SynergyEnrichment failed for $cardId", it)
                emptyList()
            }
            val hatRoles = runCatching {
                val roles = flowCatalog.rolesFor(cardId, GameFormat.HAT)
                val partnerIds = roles.flatMap { it.partnerIds }.distinct()
                val names = if (partnerIds.isEmpty()) {
                    emptyMap()
                } else {
                    dao.getByIds(partnerIds).associate { it.id to it.name }
                }
                flowCatalog.hatRoleRelated(cardId) { id -> names[id] }
            }.getOrDefault(emptyList())
            (edges + enriched + hatRoles)
                .sortedByDescending { it.score }
                .distinctBy { it.id }
                .take(want.coerceAtLeast(36))
        }

    override suspend fun ensureBundledKnowledge() {
        try {
            if (dao.formatLimitCount() == 0) {
                loadFormatLegalityFromAsset()
            } else {
                reloadLimitCache()
            }
            // Fast path only: HAT catalog + effect scripts (LUA AST). Related/synergies = on-demand.
            val coreReady = dao.meta(META_HAT_CORE) == OfflinePackAssets.PACK_VERSION
            if (!coreReady || dao.count() < 1000) {
                loadHatCatalogFromAsset()
            }
            if (!coreReady || dao.effectScriptCount() < 100) {
                // Quiet: must not leave Settings progress stuck at N/N.
                loadHatScriptsPackFromAsset(reportProgress = false)
            }
            enrichScriptsFromCardTextInternal()
            dao.putMeta(SyncMetaEntity(META_HAT_CORE, OfflinePackAssets.PACK_VERSION))
            dao.putMeta(SyncMetaEntity(META_SCRIPTS, System.currentTimeMillis().toString()))
            bump()
        } catch (t: Throwable) {
            android.util.Log.e("OfflinePack", "ensureBundledKnowledge failed", t)
            runCatching { reloadLimitCache() }
        } finally {
            // Belt-and-suspenders: background ingest must never pin the Settings busy bar.
            progress.value = null
        }
    }

    override suspend fun ensureRelatedKnowledge() {
        try {
            val relatedReady = dao.meta(META_RELATED) == OfflinePackAssets.PACK_VERSION
            val relationCount = dao.cardRelationCount()
            // Heal broken syncs that cleared edges then set META (remote 35MB OOM / empty ingest).
            if (!relatedReady || relationCount < MIN_HEALTHY_RELATIONS) {
                loadHatRelatedFromAsset()
                loadManualSynergiesFromAsset()
                dao.putMeta(SyncMetaEntity(META_RELATED, OfflinePackAssets.PACK_VERSION))
                dao.putMeta(SyncMetaEntity(META_SYNERGIES, OfflinePackAssets.PACK_VERSION))
                bump()
            } else if (dao.meta(META_SYNERGIES) != OfflinePackAssets.PACK_VERSION) {
                loadManualSynergiesFromAsset()
                bump()
            }
        } catch (t: Throwable) {
            android.util.Log.e("OfflinePack", "ensureRelatedKnowledge failed", t)
        }
    }

    override suspend fun syncCatalog(): AppResult<Int> = withContext(Dispatchers.IO) {
        try {
            var offset = 0
            var totalInserted = 0
            var page: List<com.ygochecker.core.model.Card>
            do {
                progress.value = CatalogSyncProgress(SyncPhase.CATALOG, totalInserted, totalInserted + YgoProDeckClient.PAGE_SIZE)
                page = api.fetchCatalogPage(offset)
                if (page.isNotEmpty()) {
                    dao.insertAll(page.map { it.toEntity() })
                    totalInserted += page.size
                    offset += page.size
                }
            } while (page.size >= YgoProDeckClient.PAGE_SIZE)
            dao.putMeta(SyncMetaEntity(META_CATALOG, System.currentTimeMillis().toString()))
            progress.value = null
            bump()
            AppResult.Ok(dao.count())
        } catch (_: IOException) {
            progress.value = null
            AppResult.Err(AppError("network.unavailable"))
        } catch (_: Exception) {
            progress.value = null
            AppResult.Err(AppError("catalog.unavailable"))
        }
    }

    override suspend fun syncFormatLegality(): AppResult<Int> = withContext(Dispatchers.IO) {
        try {
            progress.value = CatalogSyncProgress(SyncPhase.LEGALITY, 0, 1)
            val bytes = runCatching { api.download(YgoProDeckClient.FORMAT_LEGALITY_URL) }
                .getOrElse {
                    context.assets.open("knowledge/format-legality.json").use { it.readBytes() }
                }
            val count = ingestFormatLegalityJson(String(bytes, StandardCharsets.UTF_8))
            progress.value = null
            bump()
            AppResult.Ok(count)
        } catch (_: IOException) {
            progress.value = null
            AppResult.Err(AppError("network.unavailable"))
        } catch (_: Exception) {
            progress.value = null
            AppResult.Err(AppError("catalog.unavailable"))
        }
    }

    override suspend fun syncEffectScripts(fullRemote: Boolean): AppResult<Int> = withContext(Dispatchers.IO) {
        try {
            progress.value = CatalogSyncProgress(SyncPhase.EFFECT_SCRIPTS, 0, 1)
            val count = if (fullRemote) {
                runCatching {
                    api.openStream(YgoProDeckClient.EFFECT_SCRIPTS_URL).use { stream ->
                        ingestEffectScriptsStream(stream, reportProgress = true)
                    }
                }.getOrElse {
                    android.util.Log.w("OfflinePack", "Remote scripts failed, using HAT pack", it)
                    loadHatScriptsPackFromAsset(reportProgress = true)
                    dao.effectScriptCount()
                }
            } else {
                // Fast path: bundled HAT scripts (~6k) — preferred default.
                loadHatScriptsPackFromAsset(reportProgress = true)
                dao.effectScriptCount()
            }
            dao.putMeta(SyncMetaEntity(META_SCRIPTS, System.currentTimeMillis().toString()))
            dao.putMeta(SyncMetaEntity(META_HAT_CORE, OfflinePackAssets.PACK_VERSION))
            enrichScriptsFromCardTextInternal()
            bump()
            AppResult.Ok(count)
        } catch (_: IOException) {
            AppResult.Err(AppError("network.unavailable"))
        } catch (_: Exception) {
            AppResult.Err(AppError("catalog.unavailable"))
        } finally {
            progress.value = null
        }
    }

    override suspend fun syncRelated(): AppResult<Int> = withContext(Dispatchers.IO) {
        try {
            progress.value = CatalogSyncProgress(SyncPhase.RELATED, 0, 1)
            // Always load bundled HAT related (~compact). Remote related.json is ~35MB and
            // previously wiped local edges on OOM/timeout after clearCardRelations().
            loadHatRelatedFromAsset()
            loadManualSynergiesFromAsset()
            val count = dao.cardRelationCount()
            if (count < MIN_HEALTHY_RELATIONS) {
                progress.value = null
                return@withContext AppResult.Err(AppError("catalog.unavailable"))
            }
            dao.putMeta(SyncMetaEntity(META_RELATED, OfflinePackAssets.PACK_VERSION))
            dao.putMeta(SyncMetaEntity(META_SYNERGIES, OfflinePackAssets.PACK_VERSION))
            progress.value = CatalogSyncProgress(SyncPhase.RELATED, count, count)
            bump()
            AppResult.Ok(count)
        } catch (_: IOException) {
            progress.value = null
            AppResult.Err(AppError("network.unavailable"))
        } catch (t: Exception) {
            android.util.Log.e("OfflinePack", "syncRelated failed", t)
            progress.value = null
            AppResult.Err(AppError("catalog.unavailable"))
        } finally {
            progress.value = null
        }
    }

    override suspend fun syncAllKnowledge(): AppResult<Int> = withContext(Dispatchers.IO) {
        var total = 0
        when (val legality = syncFormatLegality()) {
            is AppResult.Err -> return@withContext legality
            is AppResult.Ok -> total += legality.value
        }
        when (val catalog = syncCatalog()) {
            is AppResult.Err -> return@withContext catalog
            is AppResult.Ok -> total += catalog.value
        }
        when (val scripts = syncEffectScripts(fullRemote = false)) {
            is AppResult.Err -> return@withContext scripts
            is AppResult.Ok -> total += scripts.value
        }
        when (val related = syncRelated()) {
            is AppResult.Err -> return@withContext related
            is AppResult.Ok -> total += related.value
        }
        AppResult.Ok(total)
    }

    override suspend fun enrichScriptsFromCardText(): AppResult<Int> = withContext(Dispatchers.IO) {
        try {
            progress.value = CatalogSyncProgress(SyncPhase.EFFECT_SCRIPTS, 0, 1)
            val n = enrichScriptsFromCardTextInternal()
            progress.value = null
            bump()
            AppResult.Ok(n)
        } catch (_: Exception) {
            progress.value = null
            AppResult.Err(AppError("catalog.unavailable"))
        }
    }

    /**
     * Boosts thin/missing LUA AST rows by reading printed card text into tags + roles.
     */
    private suspend fun enrichScriptsFromCardTextInternal(): Int {
        val cards = dao.all()
        if (cards.isEmpty()) return 0
        val existing = HashMap<Int, EffectScriptEntity>(cards.size)
        cards.map { it.id }.chunked(400).forEach { chunk ->
            dao.effectScripts(chunk).forEach { existing[it.cardId] = it }
        }
        val rows = ArrayList<EffectScriptEntity>(cards.size)
        var touched = 0
        cards.forEachIndexed { index, entity ->
            val card = Card(
                id = entity.id,
                name = entity.name,
                type = entity.type,
                race = entity.race,
                attribute = entity.attribute,
                attack = entity.attack,
                defense = entity.defense,
                level = entity.level,
                description = entity.description,
            )
            val text = EffectTextProfiler.profile(card)
            val prev = existing[entity.id]
            val mergedTags = EffectTextProfiler.mergeTags(
                EffectMechanicTags.parseCsv(prev?.tagsCsv.orEmpty()),
                text.tags,
            )
            val mergedRoles = EffectTextProfiler.mergeRoles(
                prev?.rolesCsv.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty),
                text.roles,
            )
            val next = EffectScriptEntity(
                cardId = entity.id,
                name = prev?.name?.takeIf { it.isNotBlank() } ?: entity.name,
                rolesCsv = mergedRoles.joinToString(","),
                tagsCsv = EffectMechanicTags.csv(mergedTags),
            )
            if (prev == null || prev.tagsCsv != next.tagsCsv || prev.rolesCsv != next.rolesCsv) {
                touched++
            }
            rows += next
            if (index % 400 == 0) {
                progress.value = CatalogSyncProgress(
                    SyncPhase.EFFECT_SCRIPTS,
                    index.coerceAtMost(cards.size),
                    cards.size.coerceAtLeast(1),
                )
                kotlinx.coroutines.yield()
            }
        }
        rows.chunked(250).forEach { dao.insertEffectScripts(it) }
        return touched
    }

    private suspend fun loadHatCatalogFromAsset() {
        val json = OfflinePackAssets.readGzipText(context, OfflinePackAssets.CARDS_HAT)
        val rows = OfflinePackAssets.parseCards(json)
        rows.chunked(500).forEach { dao.insertAll(it) }
        dao.putMeta(SyncMetaEntity(META_CATALOG, System.currentTimeMillis().toString()))
    }

    private suspend fun loadHatScriptsPackFromAsset(reportProgress: Boolean = true) {
        OfflinePackAssets.openGzip(context, OfflinePackAssets.SCRIPTS_HAT).use { stream ->
            ingestEffectScriptsStream(stream, reportProgress = reportProgress)
        }
    }

    private suspend fun loadHatRelatedFromAsset() {
        OfflinePackAssets.openGzip(context, OfflinePackAssets.RELATED_HAT).use { stream ->
            ingestRelatedStream(stream, expandSeries = true, reportProgress = false)
        }
    }

    private suspend fun loadManualSynergiesFromAsset() {
        val json = OfflinePackAssets.readGzipText(context, OfflinePackAssets.SYNERGIES)
        val rows = OfflinePackAssets.parseManualSynergies(json)
        if (rows.isNotEmpty()) {
            dao.insertCardRelations(rows)
        }
        dao.putMeta(SyncMetaEntity(META_SYNERGIES, OfflinePackAssets.PACK_VERSION))
    }

    private suspend fun loadFormatLegalityFromAsset(): Int {
        val json = context.assets.open("knowledge/format-legality.json").bufferedReader().use { it.readText() }
        return ingestFormatLegalityJson(json)
    }

    private suspend fun loadHatScriptsFromAsset(): Int {
        val json = context.assets.open("effect-scripts/hat-2014.json").bufferedReader().use { it.readText() }
        return ingestHatScriptsArray(json)
    }

    private suspend fun ingestFormatLegalityJson(json: String): Int {
        val root = JSONObject(json)
        val playable = root.optJSONObject("playable") ?: JSONObject()
        val maxCopies = root.optJSONObject("maxCopies") ?: JSONObject()
        val rows = ArrayList<FormatLimitEntity>(playable.length() * 3)
        val keys = playable.keys()
        while (keys.hasNext()) {
            val cardKey = keys.next()
            val cardId = cardKey.toIntOrNull() ?: continue
            val formats = playable.optJSONArray(cardKey) ?: continue
            val limits = maxCopies.optJSONObject(cardKey)
            for (i in 0 until formats.length()) {
                val format = formats.optString(i)
                if (format.isBlank()) continue
                val max = limits?.optInt(format, 3) ?: 3
                rows += FormatLimitEntity(cardId, format, max)
            }
        }
        dao.clearFormatLimits()
        rows.chunked(500).forEach { dao.insertFormatLimits(it) }
        reloadLimitCache()
        return rows.size
    }

    private suspend fun ingestHatScriptsArray(json: String): Int {
        val array = org.json.JSONArray(json)
        val rows = ArrayList<EffectScriptEntity>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val cardId = obj.optInt("cardId")
            if (cardId <= 0) continue
            val roles = obj.optJSONArray("roles")
            val rolesCsv = buildString {
                if (roles != null) {
                    for (r in 0 until roles.length()) {
                        if (isNotEmpty()) append(',')
                        append(roles.optString(r))
                    }
                }
            }
            val tags = tagsFromScriptObject(obj)
            rows += EffectScriptEntity(
                cardId = cardId,
                name = obj.optString("name"),
                rolesCsv = rolesCsv,
                tagsCsv = EffectMechanicTags.csv(tags),
            )
        }
        dao.clearEffectScripts()
        dao.insertEffectScripts(rows)
        return rows.size
    }

    private fun tagsFromScriptObject(obj: org.json.JSONObject): Set<String> {
        val timings = buildList {
            val arr = obj.optJSONArray("timings") ?: return@buildList
            for (i in 0 until arr.length()) add(arr.optString(i))
        }
        val stepsWhen = mutableListOf<String>()
        val produces = mutableListOf<String>()
        val actions = mutableListOf<EffectMechanicTags.ScriptAction>()
        val steps = obj.optJSONArray("steps")
        if (steps != null) {
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                stepsWhen += step.optString("when")
                val prod = step.optJSONArray("produces")
                if (prod != null) {
                    for (p in 0 until prod.length()) produces += prod.optString(p)
                }
                val acts = step.optJSONArray("actions")
                if (acts != null) {
                    for (a in 0 until acts.length()) {
                        val act = acts.optJSONObject(a) ?: continue
                        actions += EffectMechanicTags.ScriptAction(
                            op = act.optString("op"),
                            from = act.optString("from"),
                            to = act.optString("to"),
                            filter = act.optString("filter"),
                        )
                    }
                }
            }
        }
        return EffectMechanicTags.fromScriptJson(timings, stepsWhen, produces, actions)
    }

    private suspend fun ingestEffectScriptsStream(
        stream: java.io.InputStream,
        reportProgress: Boolean = true,
    ): Int {
        val rows = ArrayList<EffectScriptEntity>(8192)
        var expectedTotal = 0
        JsonReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "cardCount" -> {
                        expectedTotal = reader.nextInt().coerceAtLeast(0)
                        if (reportProgress && expectedTotal > 0) {
                            progress.value = CatalogSyncProgress(SyncPhase.EFFECT_SCRIPTS, 0, expectedTotal)
                        }
                    }
                    "scripts" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            reader.nextName() // card id key
                            rows += readScriptObject(reader)
                            // Update often — large luaSource (remote) makes skipValue look "stuck".
                            if (reportProgress && rows.size % 50 == 0) {
                                val total = when {
                                    expectedTotal > 0 -> expectedTotal
                                    else -> rows.size + 50
                                }
                                progress.value = CatalogSyncProgress(
                                    SyncPhase.EFFECT_SCRIPTS,
                                    rows.size.coerceAtMost(total),
                                    total,
                                )
                                kotlinx.coroutines.yield()
                            }
                        }
                        reader.endObject()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        // Prefer actual row count over cardCount metadata (remote packs can disagree).
        val total = rows.size.coerceAtLeast(1)
        if (reportProgress) {
            progress.value = CatalogSyncProgress(SyncPhase.EFFECT_SCRIPTS, 0, total)
        }
        dao.clearEffectScripts()
        rows.chunked(250).forEachIndexed { index, chunk ->
            dao.insertEffectScripts(chunk)
            if (reportProgress) {
                val done = ((index + 1) * 250).coerceAtMost(total)
                progress.value = CatalogSyncProgress(SyncPhase.EFFECT_SCRIPTS, done, total)
                kotlinx.coroutines.yield()
            }
        }
        if (reportProgress) {
            progress.value = CatalogSyncProgress(SyncPhase.EFFECT_SCRIPTS, total, total)
        }
        return rows.size
    }

    private fun readScriptObject(reader: JsonReader): EffectScriptEntity {
        var cardId = 0
        var name = ""
        val roles = mutableListOf<String>()
        val timings = mutableListOf<String>()
        val stepsWhen = mutableListOf<String>()
        val produces = mutableListOf<String>()
        val actions = mutableListOf<EffectMechanicTags.ScriptAction>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "cardId" -> cardId = reader.nextInt()
                "name" -> name = reader.nextString()
                "roles" -> {
                    reader.beginArray()
                    while (reader.hasNext()) roles += reader.nextString()
                    reader.endArray()
                }
                "timings" -> {
                    reader.beginArray()
                    while (reader.hasNext()) timings += reader.nextString()
                    reader.endArray()
                }
                "steps" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "when" -> stepsWhen += reader.nextString()
                                "produces" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) produces += reader.nextString()
                                    reader.endArray()
                                }
                                "actions" -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        var op = ""
                                        var from = ""
                                        var to = ""
                                        var filter = ""
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "op" -> op = reader.nextString()
                                                "from" -> from = reader.nextString()
                                                "to" -> to = reader.nextString()
                                                "filter" -> filter = reader.nextString()
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                        actions += EffectMechanicTags.ScriptAction(op, from, to, filter)
                                    }
                                    reader.endArray()
                                }
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val tags = EffectMechanicTags.fromScriptJson(timings, stepsWhen, produces, actions)
        return EffectScriptEntity(
            cardId = cardId,
            name = name,
            rolesCsv = roles.joinToString(","),
            tagsCsv = EffectMechanicTags.csv(tags),
        )
    }

    /**
     * Streams related.json: merges mechanic tags into effect_scripts and stores Related rows.
     */
    private suspend fun ingestRelatedStream(
        stream: java.io.InputStream,
        expandSeries: Boolean = false,
        reportProgress: Boolean = true,
    ): Int {
        val relations = ArrayList<CardRelationEntity>(160_000)
        val tagsByCard = HashMap<Int, MutableSet<String>>(16_000)
        val seriesMembers = HashMap<String, MutableList<Pair<Int, String>>>(2_000)
        var entries = 0
        JsonReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "entries" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            val sourceId = key.toIntOrNull()
                            if (sourceId == null) {
                                reader.skipValue()
                            } else {
                                readRelatedEntry(reader, sourceId, relations, tagsByCard, seriesMembers)
                                entries++
                                if (reportProgress && entries % 500 == 0) {
                                    progress.value = CatalogSyncProgress(SyncPhase.RELATED, entries, entries + 500)
                                }
                            }
                        }
                        reader.endObject()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        if (expandSeries) {
            appendSeriesPeers(relations, seriesMembers)
        }
        // Never wipe existing edges unless we actually parsed replacements.
        if (relations.isEmpty()) {
            android.util.Log.w("OfflinePack", "ingestRelatedStream produced 0 relations — keeping previous rows")
            return 0
        }
        dao.clearCardRelations()
        relations.chunked(800).forEach { dao.insertCardRelations(it) }
        mergeTagsIntoScripts(tagsByCard)
        return entries
    }

    private suspend fun appendSeriesPeers(
        relations: MutableList<CardRelationEntity>,
        seriesMembers: Map<String, List<Pair<Int, String>>>,
    ) {
        val allIds = seriesMembers.values.flatten().map { it.first }.distinct()
        val names = allIds.chunked(400)
            .flatMap { dao.getByIds(it) }
            .associate { it.id to it.name }
        val seen = HashSet<String>()
        for ((_, members) in seriesMembers) {
            if (members.size < 2) continue
            val capped = members
                .map { (id, fallback) -> id to (names[id] ?: fallback) }
                .take(40)
            for (i in capped.indices) {
                val (srcId, _) = capped[i]
                var added = 0
                for (j in capped.indices) {
                    if (i == j) continue
                    val (tgtId, tgtName) = capped[j]
                    val key = "$srcId:$tgtId:series_peer"
                    if (!seen.add(key)) continue
                    relations += CardRelationEntity(
                        sourceId = srcId,
                        targetId = tgtId,
                        relation = "series_peer",
                        score = 1.35,
                        targetName = tgtName,
                    )
                    added++
                    if (added >= 14) break
                }
            }
        }
    }

    private fun readRelatedEntry(
        reader: JsonReader,
        sourceId: Int,
        relations: MutableList<CardRelationEntity>,
        tagsByCard: MutableMap<Int, MutableSet<String>>,
        seriesMembers: MutableMap<String, MutableList<Pair<Int, String>>>,
    ) {
        val tags = mutableListOf<String>()
        val series = mutableListOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "tags" -> {
                    reader.beginArray()
                    while (reader.hasNext()) tags += reader.nextString()
                    reader.endArray()
                }
                "series" -> {
                    reader.beginArray()
                    while (reader.hasNext()) series += reader.nextString()
                    reader.endArray()
                }
                "related" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        var targetId = 0
                        var name = ""
                        var relation = ""
                        var score = 0.0
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "id" -> targetId = reader.nextInt()
                                "name" -> name = reader.nextString()
                                "relation" -> relation = reader.nextString()
                                "score" -> score = reader.nextDouble()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (targetId > 0 && relation.isNotBlank()) {
                            relations += CardRelationEntity(
                                sourceId = sourceId,
                                targetId = targetId,
                                relation = relation,
                                score = score,
                                targetName = name,
                            )
                        }
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        for (s in series) {
            if (s.isBlank()) continue
            seriesMembers.getOrPut(s) { ArrayList() }.add(sourceId to "#$sourceId")
        }
        val mechanics = EffectMechanicTags.fromRelatedTags(tags)
        if (mechanics.isNotEmpty()) {
            tagsByCard.getOrPut(sourceId) { linkedSetOf() }.addAll(mechanics)
        }
    }

    private suspend fun mergeTagsIntoScripts(tagsByCard: Map<Int, Set<String>>) {
        if (tagsByCard.isEmpty()) return
        val existing = tagsByCard.keys.chunked(400)
            .flatMap { dao.effectScripts(it) }
            .associateBy { it.cardId }
        val upserts = ArrayList<EffectScriptEntity>(tagsByCard.size)
        for ((cardId, extra) in tagsByCard) {
            val row = existing[cardId]
            val merged = LinkedHashSet<String>()
            if (row != null) merged.addAll(EffectMechanicTags.parseCsv(row.tagsCsv))
            merged.addAll(extra)
            upserts += EffectScriptEntity(
                cardId = cardId,
                name = row?.name.orEmpty(),
                rolesCsv = row?.rolesCsv.orEmpty(),
                tagsCsv = EffectMechanicTags.csv(merged),
            )
        }
        upserts.chunked(500).forEach { dao.insertEffectScripts(it) }
    }

    private suspend fun reloadLimitCache() {
        limitCache.clear()
        knownLimitCards.clear()
        dao.allFormatLimits().forEach { row ->
            limitCache["${row.cardId}:${row.format}"] = row.maxCopies
            knownLimitCards.add(row.cardId)
        }
    }

    private fun bump() {
        statusTick.value = statusTick.value + 1
    }

    private companion object {
        const val META_CATALOG = "catalog_synced_at"
        const val META_SCRIPTS = "scripts_synced_at"
        const val META_HAT_CORE = "hat_offline_core"
        const val META_RELATED = "hat_related_pack"
        const val META_SYNERGIES = "manual_synergies"
        /** HAT related pack is ~20k+ edges; below this we treat DB as broken and reload. */
        const val MIN_HEALTHY_RELATIONS = 5_000
    }
}

private fun ByteArray.inputStream() = java.io.ByteArrayInputStream(this)

private fun EffectScriptEntity.toSummary() = EffectScriptSummary(
    cardId = cardId,
    name = name,
    roles = rolesCsv.split(',').map(String::trim).filter(String::isNotEmpty),
    tags = EffectMechanicTags.parseCsv(tagsCsv),
)
