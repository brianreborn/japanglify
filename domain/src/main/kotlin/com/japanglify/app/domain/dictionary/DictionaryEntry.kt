package com.japanglify.app.domain.dictionary

/**
 * One dictionary lookup result. v1 always carries a single, primary sense —
 * see [com.japanglify.app.domain.dictionary.GlossAnnotator] and the plan's
 * explicit "sense disambiguation" backlog note for why that's a documented
 * v1 limitation, not an oversight.
 */
data class DictionaryEntry(
    val headword: String,
    val reading: String?,
    /** Null when the source entry's part of speech didn't map to anything known. */
    val partOfSpeech: PartOfSpeech?,
    /** First/primary sense's English gloss text, e.g. "paper" for 紙. */
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
