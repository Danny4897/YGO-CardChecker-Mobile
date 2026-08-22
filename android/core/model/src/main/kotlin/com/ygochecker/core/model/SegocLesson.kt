package com.ygochecker.core.model

enum class SegocStepKind {
    EVENT,
    YOUR_TRIGGERS,
    YOU_ORDER,
    OPP_ORDER,
    RESOLVE_LIFO,
}

data class SegocStepCard(
    val cardId: Int,
    val spellSpeed: Int?,
    val missedTimingRisk: Boolean,
    val zoneHint: String? = null,
)

data class SegocStep(
    val kind: SegocStepKind,
    val bodyKey: String,
    val cards: List<SegocStepCard>,
    val event: TriggerEvent?,
)

data class SegocLesson(
    val id: String,
    val event: TriggerEvent,
    val yourCardIds: List<Int>,
    val oppCardIds: List<Int>,
    val steps: List<SegocStep>,
)

fun buildSegocLessons(
    yourIds: Collection<Int>,
    oppIds: Collection<Int> = emptyList(),
    profiles: Map<Int, SegocProfileSummary>,
): List<SegocLesson> {
    val yourSet = yourIds.toSet()
    val oppSet = oppIds.toSet()
    val events = TriggerEvent.entries.filter { it != TriggerEvent.OTHER }
    val lessons = mutableListOf<SegocLesson>()
    for (event in events) {
        val yours = yourSet.filter { id ->
            val p = profiles[id] ?: return@filter false
            p.effectType == SegocEffectType.TRIGGER && event in p.triggerEvents
        }.sorted()
        val opps = oppSet.filter { id ->
            val p = profiles[id] ?: return@filter false
            p.effectType == SegocEffectType.TRIGGER && event in p.triggerEvents
        }.sorted()
        val emit = yours.size >= 2 || (yours.isNotEmpty() && opps.isNotEmpty())
        if (!emit) continue
        lessons += lessonFor(event, yours, opps, profiles)
    }
    return lessons
}

private fun lessonFor(
    event: TriggerEvent,
    yours: List<Int>,
    opps: List<Int>,
    profiles: Map<Int, SegocProfileSummary>,
): SegocLesson {
    fun cards(ids: List<Int>) = ids.map { id ->
        val p = profiles[id]
        SegocStepCard(
            cardId = id,
            spellSpeed = p?.spellSpeed,
            missedTimingRisk = p?.missedTimingRisk == true,
        )
    }
    val steps = mutableListOf(
        SegocStep(SegocStepKind.EVENT, "flow_lesson_event", emptyList(), event),
        SegocStep(SegocStepKind.YOUR_TRIGGERS, "flow_lesson_your_triggers", cards(yours), event),
        SegocStep(SegocStepKind.YOU_ORDER, "flow_lesson_you_order", cards(yours), event),
    )
    if (opps.isNotEmpty()) {
        steps += SegocStep(SegocStepKind.OPP_ORDER, "flow_lesson_opp_order", cards(opps), event)
    }
    steps += SegocStep(
        SegocStepKind.RESOLVE_LIFO,
        "flow_lesson_resolve_lifo",
        cards(yours + opps),
        event,
    )
    val id = "lesson-${event.name}-${yours.joinToString("-")}-${opps.joinToString("-")}"
    return SegocLesson(
        id = id,
        event = event,
        yourCardIds = yours,
        oppCardIds = opps,
        steps = steps,
    )
}
