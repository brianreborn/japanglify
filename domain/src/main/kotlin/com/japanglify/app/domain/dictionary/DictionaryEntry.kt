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
