package com.japanglify.app.domain.dictionary

/**
 * One dictionary lookup result — always a single, already-resolved sense
 * (see [SenseSelector] for how the winning sense among a headword's
 * candidates is chosen, and [GlossAnnotator] for how it's rendered).
 */
data class DictionaryEntry(
    val headword: String,
    val reading: String?,
    /**
     * The real category, always populated when the source data maps to one.
     * Never shown in the rendered gloss text (see [GlossAnnotator.format])
     * -- used purely for logic: particle-gloss omission, and
     * [com.japanglify.app.domain.emoji.EmojiAnnotator]'s POS-scope filter.
     */
    val partOfSpeech: PartOfSpeech?,
    /** The winning sense's English gloss text, e.g. "paper" for 紙. */
    val gloss: String
)

/**
 * Standard English lexicographic part-of-speech abbreviations — chosen to
 * match existing dictionary convention (n., v., adj., ...) rather than
 * inventing new notation, per this project's stated preference for existing
 * linguistic/lexicographic conventions wherever possible.
 */
enum class PartOfSpeech(val abbreviation: String, val displayName: String) {
    NOUN("n.", "Noun"),
    VERB("v.", "Verb"),
    ADJECTIVE("adj.", "Adjective"),
    ADVERB("adv.", "Adverb"),
    PARTICLE("part.", "Particle"),
    PRONOUN("pron.", "Pronoun"),
    CONJUNCTION("conj.", "Conjunction"),
    INTERJECTION("interj.", "Interjection"),
    AUXILIARY("aux.", "Auxiliary"),
    PREFIX("pref.", "Prefix"),
    SUFFIX("suf.", "Suffix"),
    COUNTER("ctr.", "Counter"),
    EXPRESSION("expr.", "Expression"),
    OTHER("", "Other");

    companion object {
        /**
         * Maps a JMdict part-of-speech code (e.g. "v5r", "adj-i", "n-adv",
         * "prt") to a coarse [PartOfSpeech]. JMdict has dozens of granular
         * codes (godan-verb subtypes by ending, etc.) — v1 only needs the
         * broad category, so this matches by prefix rather than enumerating
         * every one of JMdict's documented tags individually.
         */
        fun fromJmdictCode(code: String): PartOfSpeech = when {
            code.startsWith("v") -> VERB
            code.startsWith("adj") -> ADJECTIVE
            code.startsWith("adv") -> ADVERB
            code.startsWith("n") -> NOUN
            code.startsWith("prt") -> PARTICLE
            code.startsWith("pn") -> PRONOUN
            code.startsWith("conj") -> CONJUNCTION
            code.startsWith("int") -> INTERJECTION
            code.startsWith("aux") -> AUXILIARY
            code.startsWith("pref") -> PREFIX
            code.startsWith("suf") -> SUFFIX
            code.startsWith("ctr") -> COUNTER
            code.startsWith("exp") -> EXPRESSION
            else -> OTHER
        }
    }
}

/**
 * Maps Kuromoji/IPADIC's own verb conjugation-class classification (e.g.
 * "五段・ラ行", "サ変・スル", "カ変・来ル") to the matching JMdict verb-class
 * code PREFIX ("v5r", "vs", "vk") stored in this app's `pos` column —
 * translating between the two vocabularies the same way
 * [PartOfSpeech.fromJmdictCode] already does for JMdict's own codes.
 *
 * Purpose: several distinct JMdict *words* can share one kana spelling+reading
 * (する is also the kana reading of 擦る "to rub", 刷る "to print", 剃る "to
 * shave" — unrelated verbs, not alternate senses of one word), and nothing in
 * [SenseCandidate] previously distinguished them, so [SenseSelector] just
 * scored richest-gloss-wins across all of them regardless of which word
 * Kuromoji actually parsed this token as — found live: する ("to do")
 * rendered as "to rub" because 擦る's entry happened to list more English
 * synonyms. Kuromoji already knows the difference (a light-verb する instance
 * is tagged サ変・スル, a real 擦る instance is tagged 五段・ラ行) — this hint
 * lets a lookup prefer the DB row whose stored JMdict verb class actually
 * matches, without needing to touch which rows get imported at all (an
 * earlier import-time attempt at this same problem broke 来る "to come" and
 * is why this lives at lookup time instead — see git history).
 *
 * Null for anything Kuromoji doesn't classify as an ordinary modern verb
 * conjugation (particles/nouns report conjugationType "*"; classical/literary
 * forms use their own scheme) — callers must treat null as "no hint, don't
 * filter," not "no match."
 */
fun jmdictVerbConjugationPrefix(kuromojiConjugationType: String?): String? {
    if (kuromojiConjugationType == null || kuromojiConjugationType == "*") return null
    return when {
        kuromojiConjugationType.startsWith("サ変") -> "vs"
        kuromojiConjugationType.startsWith("カ変") -> "vk"
        kuromojiConjugationType == "一段" -> "v1"
        kuromojiConjugationType.startsWith("五段") -> when {
            kuromojiConjugationType.contains("カ行") -> "v5k"
            kuromojiConjugationType.contains("ガ行") -> "v5g"
            kuromojiConjugationType.contains("サ行") -> "v5s"
            kuromojiConjugationType.contains("タ行") -> "v5t"
            kuromojiConjugationType.contains("ナ行") -> "v5n"
            kuromojiConjugationType.contains("バ行") -> "v5b"
            kuromojiConjugationType.contains("マ行") -> "v5m"
            kuromojiConjugationType.contains("ラ行") -> "v5r"
            kuromojiConjugationType.contains("ワ行") -> "v5u"
            else -> null
        }
        else -> null
    }
}
