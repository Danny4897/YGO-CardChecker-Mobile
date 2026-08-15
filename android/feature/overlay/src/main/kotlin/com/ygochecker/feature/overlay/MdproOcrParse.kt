package com.ygochecker.feature.overlay

data class MdproOcrParseResult(
    val passcodes: List<Int>,
    val candidateName: String?,
    val rawLines: List<String>,
)

/**
 * Parse MDPRO / Master Duel card-detail OCR text.
 * Prefers unique passcodes (esp. `[99370594]`); name is fallback only.
 * Port of `ygo-card-checker/src/app/utils/mdpro-ocr-parse.ts`.
 */
object MdproOcrParse {
    private val bracketPasscodeRe = Regex("""\[(\d{7,8})]""")
    private val passcodeRe = Regex("""\b(\d{7,8})\b""")
    private val bracketMetaRe = Regex("""^\[[^\]]+](\s*\[[^\]]+])*\s*$""")
    private val slashGroupRe = Regex("""\[([\d/OoIl|]+)]""")
    private val noiseLineRe = Regex(
        """^(save image|atk|def|ocg|tcg|g:\d+|test hand|sort|save|\+\d+|-\d+|spell card|trap card|monster card|normal spell|quick-play spell|continuous spell|equip spell|field spell|ritual spell|normal trap|continuous trap|counter trap|carta magia|carta trappola|carta mostro|magia normale|magia rapida|magia continua|magia equipaggiamento|magia terreno|magia rituale|trappola normale|trappola continua|controtrappola)$""",
        RegexOption.IGNORE_CASE,
    )
    private val digitBracketInnerRe = Regex("""^[\d/OoIl|SsBbGg\s]+$""")

    fun parse(text: String): MdproOcrParseResult {
        val normalized = normalizeOcrDigitNoise(text)
        val rawLines = normalized
            .split(Regex("""\r?\n"""))
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotEmpty() }
        return MdproOcrParseResult(
            passcodes = extractPasscodes(normalized),
            candidateName = pickCandidateName(rawLines),
            rawLines = rawLines,
        )
    }

    fun cleanCardName(name: String): String =
        name
            .replace(Regex("""^["'`]+|["'`]+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    fun normalizeCardName(name: String): String =
        cleanCardName(name).lowercase().replace(Regex("""[^a-z0-9]+"""), "")

    /** Stable identity key for deduping OCR lookups — passcode wins over name. */
    fun identityKey(parsed: MdproOcrParseResult): String {
        if (parsed.passcodes.isNotEmpty()) return "id:${parsed.passcodes.first()}"
        val name = parsed.candidateName ?: return ""
        return "name:${normalizeCardName(name)}"
    }

    fun matchesKnownCard(
        parsed: MdproOcrParseResult,
        cardId: Int?,
        cardName: String?,
    ): Boolean {
        if (cardId != null && parsed.passcodes.contains(cardId)) return true
        if (cardName.isNullOrBlank() || parsed.candidateName.isNullOrBlank()) return false
        val a = normalizeCardName(cardName)
        val b = normalizeCardName(parsed.candidateName)
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    private fun extractPasscodes(text: String): List<Int> {
        val passcodes = mutableListOf<Int>()
        val seen = mutableSetOf<Int>()

        fun push(raw: String) {
            val id = raw.toLongOrNull()?.toInt() ?: return
            if (id < 1_000_000) return
            if (seen.add(id)) passcodes.add(id)
        }

        bracketPasscodeRe.findAll(text).forEach { push(it.groupValues[1]) }

        slashGroupRe.findAll(text).forEach { match ->
            val inner = match.groupValues[1]
            for (part in inner.split('/')) {
                val digits = part.replace(Regex("""[^\d]"""), "")
                if (digits.length in 7..8) push(digits)
            }
        }

        passcodeRe.findAll(text).forEach { push(it.groupValues[1]) }
        return passcodes
    }

    /** OCR often confuses O/0 and I/l/1 inside digit-only bracket groups. */
    private fun normalizeOcrDigitNoise(text: String): String =
        text.replace(Regex("""\[([^\]]+)]""")) { match ->
            val inner = match.groupValues[1]
            if (!digitBracketInnerRe.matches(inner)) return@replace match.value
            val fixed = inner
                .replace(Regex("""[Oo]"""), "0")
                .replace(Regex("""[Il|]"""), "1")
                .replace(Regex("""[Ss]"""), "5")
                .replace(Regex("""[Bb]"""), "8")
                .replace(Regex("""[Gg]"""), "6")
            "[$fixed]"
        }

    private fun pickCandidateName(lines: List<String>): String? {
        for (line in lines) {
            if (bracketMetaRe.matches(line)) continue
            if (noiseLineRe.matches(line)) continue
            if (Regex("""^atk\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(line)) continue
            if (Regex("""^def\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(line)) continue
            if (Regex("""^\d{1,2}$""").matches(line)) continue
            if (
                Regex(
                    """^(normal|quick-?play|continuous|equip|field|ritual)\s+(spell|trap)$""",
                    RegexOption.IGNORE_CASE,
                ).matches(line)
            ) {
                continue
            }
            if (
                Regex("""^(magia|trappola)\s+""", RegexOption.IGNORE_CASE).containsMatchIn(line) &&
                line.split(Regex("""\s+""")).size <= 3
            ) {
                continue
            }
            if (Regex("""^[\d/\s]+$""").matches(line)) continue
            val cleaned = cleanCardName(line)
            if (Regex("""[A-Za-zÀ-ÿ]""").containsMatchIn(cleaned) && cleaned.length >= 3) {
                return cleaned
            }
        }
        return null
    }
}
