package com.ygochecker.data.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowPackParserTest {
    @Test
    fun `parses hat flow seed sample`() {
        val json = """
            {
              "flows": [{
                "id": "hat-hand-bait",
                "formatId": "hat",
                "title": "Hand bait",
                "kind": "BAIT_REACTION",
                "roleTags": ["Control"],
                "metrics": { "steps": 2, "handCost": 1, "risk": "MED", "payoff": "BOARD" },
                "startNodeId": "h0",
                "nodes": [
                  { "id": "h0", "sortIndex": 0, "title": "Start", "cardIds": [68535320] },
                  { "id": "h1", "sortIndex": 1, "title": "CL1", "timingWindow": "hand_destruction_cl1" }
                ],
                "edges": [
                  { "id": "e0", "from": "h0", "to": "h1", "label": "go", "condition": "ALWAYS", "isDefault": true }
                ],
                "chokePoints": [
                  {
                    "nodeId": "h1",
                    "severity": "HIGH",
                    "reason": "Miss timing",
                    "recoveryCardIds": [54447022],
                    "suggestedCopies": 1
                  }
                ]
              }]
            }
        """.trimIndent()
        val flows = FlowPackParser.parseFlows(json)
        assertEquals(1, flows.size)
        assertEquals("hat-hand-bait", flows[0].id)
        assertEquals("hand_destruction_cl1", flows[0].nodes["h1"]?.timingWindow)
        assertTrue(flows[0].defaultEdge("h0")!!.isDefault)
        assertEquals(1, flows[0].chokePoints.size)
        assertEquals(54447022, flows[0].chokePoints.single().recoveryCardIds.single())
    }

    @Test
    fun `parses card roles`() {
        val json = """
            {
              "formatId": "hat",
              "roles": [
                { "cardId": 68535320, "role": "BAIT", "priority": 9, "partnerIds": [95929069] }
              ]
            }
        """.trimIndent()
        val roles = FlowPackParser.parseRoles(json)
        assertEquals(1, roles.size)
        assertEquals(95929069, roles[0].partnerIds.single())
    }
}
