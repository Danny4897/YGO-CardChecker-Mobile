package com.ygochecker.data.cards

import com.ygochecker.core.model.FlowChokePoint
import com.ygochecker.core.model.FlowEdge
import com.ygochecker.core.model.FlowEdgeCondition
import com.ygochecker.core.model.FlowGraph
import com.ygochecker.core.model.FlowKind
import com.ygochecker.core.model.FlowMetrics
import com.ygochecker.core.model.FlowNode
import com.ygochecker.core.model.FlowPayoff
import com.ygochecker.core.model.FlowRisk
import com.ygochecker.core.model.FormatCardRole
import com.ygochecker.core.model.HatCardRole
import org.json.JSONArray
import org.json.JSONObject

internal object FlowPackParser {
    fun parseFlows(json: String): List<FlowGraph> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("flows") ?: return emptyList()
        val out = ArrayList<FlowGraph>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseOne(o)?.let(out::add)
        }
        return out
    }

    fun parseRoles(json: String): List<FormatCardRole> {
        val root = JSONObject(json)
        val formatId = root.optString("formatId").ifBlank { "hat" }
        val arr = root.optJSONArray("roles") ?: return emptyList()
        val out = ArrayList<FormatCardRole>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val cardId = o.optInt("cardId")
            if (cardId <= 0) continue
            val role = runCatching { HatCardRole.valueOf(o.optString("role")) }.getOrNull() ?: continue
            out += FormatCardRole(
                cardId = cardId,
                formatId = formatId,
                role = role,
                priority = o.optInt("priority", 0),
                rulingNotes = o.optString("rulingNotes").ifBlank { null },
                partnerIds = o.optJSONArray("partnerIds").toIntList(),
            )
        }
        return out
    }

    private fun parseOne(o: JSONObject): FlowGraph? {
        val id = o.optString("id").ifBlank { return null }
        val nodesArr = o.optJSONArray("nodes") ?: return null
        val nodes = linkedMapOf<String, FlowNode>()
        for (i in 0 until nodesArr.length()) {
            val n = nodesArr.optJSONObject(i) ?: continue
            val nid = n.optString("id").ifBlank { continue }
            nodes[nid] = FlowNode(
                id = nid,
                sortIndex = n.optInt("sortIndex", i),
                title = n.optString("title"),
                body = n.optString("body").ifBlank { null },
                timingWindow = n.optString("timingWindow").ifBlank { null },
                cardIds = n.optJSONArray("cardIds").toIntList(),
                zoneHint = n.optString("zoneHint").ifBlank { null },
            )
        }
        if (nodes.isEmpty()) return null
        val edgesArr = o.optJSONArray("edges") ?: JSONArray()
        val edges = ArrayList<FlowEdge>(edgesArr.length())
        for (i in 0 until edgesArr.length()) {
            val e = edgesArr.optJSONObject(i) ?: continue
            val from = e.optString("from")
            val to = e.optString("to")
            if (from !in nodes || to !in nodes) continue
            edges += FlowEdge(
                id = e.optString("id").ifBlank { "e$i" },
                from = from,
                to = to,
                label = e.optString("label"),
                condition = runCatching {
                    FlowEdgeCondition.valueOf(e.optString("condition", "ALWAYS"))
                }.getOrDefault(FlowEdgeCondition.ALWAYS),
                isDefault = e.optBoolean("isDefault", false),
            )
        }
        val metricsObj = o.optJSONObject("metrics") ?: JSONObject()
        val start = o.optString("startNodeId").ifBlank { nodes.keys.first() }
        if (start !in nodes) return null
        val kind = runCatching { FlowKind.valueOf(o.optString("kind")) }.getOrDefault(FlowKind.OPENING)
        val chokeArr = o.optJSONArray("chokePoints") ?: JSONArray()
        val chokes = ArrayList<FlowChokePoint>(chokeArr.length())
        for (i in 0 until chokeArr.length()) {
            val c = chokeArr.optJSONObject(i) ?: continue
            val nodeId = c.optString("nodeId").ifBlank { continue }
            if (nodeId !in nodes) continue
            chokes += FlowChokePoint(
                nodeId = nodeId,
                severity = runCatching {
                    FlowRisk.valueOf(c.optString("severity", "HIGH"))
                }.getOrDefault(FlowRisk.HIGH),
                reason = c.optString("reason").ifBlank { "Choke at $nodeId" },
                recoveryCardIds = c.optJSONArray("recoveryCardIds").toIntList(),
                suggestedCopies = c.optInt("suggestedCopies", 1).coerceAtLeast(1),
            )
        }
        return FlowGraph(
            id = id,
            formatId = o.optString("formatId").ifBlank { "hat" },
            title = o.optString("title"),
            archetype = o.optString("archetype").ifBlank { null },
            kind = kind,
            roleTags = o.optJSONArray("roleTags").toStringList(),
            metrics = FlowMetrics(
                steps = metricsObj.optInt("steps", nodes.size),
                handCost = metricsObj.optInt("handCost", 0),
                fieldCost = metricsObj.optInt("fieldCost", 0),
                risk = runCatching {
                    FlowRisk.valueOf(metricsObj.optString("risk", "MED"))
                }.getOrDefault(FlowRisk.MED),
                payoff = runCatching {
                    FlowPayoff.valueOf(metricsObj.optString("payoff", "CONTROL"))
                }.getOrDefault(FlowPayoff.CONTROL),
            ),
            nodes = nodes,
            edges = edges,
            startNodeId = start,
            chokePoints = chokes,
        )
    }
}

private fun JSONArray?.toIntList(): List<Int> {
    if (this == null) return emptyList()
    val out = ArrayList<Int>(length())
    for (i in 0 until length()) {
        val v = optInt(i, 0)
        if (v > 0) out += v
    }
    return out
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val v = optString(i)
        if (v.isNotBlank()) out += v
    }
    return out
}
