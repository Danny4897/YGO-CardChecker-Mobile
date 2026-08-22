package com.ygochecker.core.model

enum class PuzzleWin {
    CHAIN_ORDER,
    MUST_CL1,
    APNAP_YOU_FIRST,
}

enum class FieldOwner { YOU, OPP }

enum class FieldArea { MONSTER, ST, EXTRA_MONSTER, GY, BANISH, DECK, EXTRA, FIELD_SPELL, HAND }

data class FieldZone(
    val owner: FieldOwner,
    val area: FieldArea,
    val index: Int = 0,
)

data class PuzzleBoardSlot(
    val zone: FieldZone,
    val cardId: Int?,
)

data class PuzzleInstance(
    val id: String,
    val templateId: String,
    val lesson: SegocLesson,
    val board: List<PuzzleBoardSlot>,
    val win: PuzzleWin,
    val expectedChain: List<Int>,
    val opponentDeckId: Long? = null,
)

fun instantiatePuzzles(
    yourIds: Collection<Int>,
    oppIds: Collection<Int> = emptyList(),
    profiles: Map<Int, SegocProfileSummary>,
    max: Int = 12,
): List<PuzzleInstance> {
    val lessons = buildSegocLessons(yourIds, oppIds, profiles)
    val out = mutableListOf<PuzzleInstance>()
    for (lesson in lessons) {
        missedTimingPuzzle(lesson, profiles)?.let { out += it }
        if (lesson.yourCardIds.size >= 2) {
            out += lifoYourTwo(lesson)
        }
        if (lesson.yourCardIds.isNotEmpty() && lesson.oppCardIds.isNotEmpty()) {
            out += apnapYouVsOpp(lesson)
        }
        if (out.size >= max) break
    }
    return out.take(max)
}

fun evaluatePuzzle(puzzle: PuzzleInstance, placed: List<Int>): Boolean = when (puzzle.win) {
    PuzzleWin.CHAIN_ORDER -> placed == puzzle.expectedChain
    PuzzleWin.MUST_CL1 -> placed.firstOrNull() == puzzle.expectedChain.firstOrNull() &&
        placed.toSet() == puzzle.expectedChain.toSet()
    PuzzleWin.APNAP_YOU_FIRST -> apnapOk(placed, puzzle.lesson.yourCardIds, puzzle.lesson.oppCardIds)
}

private fun apnapOk(placed: List<Int>, yours: List<Int>, opps: List<Int>): Boolean {
    val yourSet = yours.toSet()
    val oppSet = opps.toSet()
    if (placed.toSet() != yourSet + oppSet) return false
    if (placed.size != yourSet.size + oppSet.size) return false
    var seenOpp = false
    for (id in placed) {
        if (id in oppSet) seenOpp = true
        if (id in yourSet && seenOpp) return false
    }
    return true
}

private fun lifoYourTwo(lesson: SegocLesson): PuzzleInstance {
    val a = lesson.yourCardIds[0]
    val b = lesson.yourCardIds[1]
    val low = minOf(a, b)
    val high = maxOf(a, b)
    return PuzzleInstance(
        id = "lifo-${lesson.id}",
        templateId = "lifo_your_two",
        lesson = lesson,
        board = listOf(
            PuzzleBoardSlot(FieldZone(FieldOwner.YOU, FieldArea.GY), low),
            PuzzleBoardSlot(FieldZone(FieldOwner.YOU, FieldArea.MONSTER, 1), high),
        ),
        win = PuzzleWin.CHAIN_ORDER,
        expectedChain = listOf(low, high),
    )
}

private fun apnapYouVsOpp(lesson: SegocLesson): PuzzleInstance = PuzzleInstance(
    id = "apnap-${lesson.id}",
    templateId = "apnap_you_vs_opp",
    lesson = lesson,
    board = listOf(
        PuzzleBoardSlot(FieldZone(FieldOwner.YOU, FieldArea.MONSTER, 2), lesson.yourCardIds.first()),
        PuzzleBoardSlot(FieldZone(FieldOwner.OPP, FieldArea.MONSTER, 2), lesson.oppCardIds.first()),
    ),
    win = PuzzleWin.APNAP_YOU_FIRST,
    expectedChain = lesson.yourCardIds + lesson.oppCardIds,
)

private fun missedTimingPuzzle(
    lesson: SegocLesson,
    profiles: Map<Int, SegocProfileSummary>,
): PuzzleInstance? {
    val whenCard = lesson.yourCardIds.firstOrNull { profiles[it]?.missedTimingRisk == true } ?: return null
    val other = lesson.yourCardIds.firstOrNull { it != whenCard } ?: return null
    return PuzzleInstance(
        id = "miss-${lesson.id}",
        templateId = "missed_timing_when",
        lesson = lesson,
        board = listOf(
            PuzzleBoardSlot(FieldZone(FieldOwner.YOU, FieldArea.HAND), whenCard),
            PuzzleBoardSlot(FieldZone(FieldOwner.YOU, FieldArea.ST, 2), other),
        ),
        win = PuzzleWin.MUST_CL1,
        expectedChain = listOf(whenCard, other),
    )
}
