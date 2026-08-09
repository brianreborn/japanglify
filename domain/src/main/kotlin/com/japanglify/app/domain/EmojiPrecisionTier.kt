package com.japanglify.app.domain

/**
 * How strict a match against CLDR's emoji names has to be before the
 * optional English→emoji annotation fires, each strictly widening the last:
 *
 * - [STRICT]: exact match against an emoji's single canonical short name
 *   (CLDR `tts`), unique across the whole dataset.
 * - [MEDIUM]: STRICT, then a real WordNet *synonym* expansion (e.g.
 *   "automobile" matching via its synonym "car") if STRICT found nothing.
 * - [LOOSE]: MEDIUM, then three further fallbacks if that still finds
 *   nothing: a word claimed by exactly one emoji's broader CLDR keyword
 *   list (not its `tts`), then a WordNet *hypernym* expansion one hop up
 *   (e.g. "jacket" matching via its category "coat"), then
 *   [com.japanglify.app.domain.emoji.CategoryEmoji]'s small, hand-reviewed
 *   table for category words with no single representative emoji of their
 *   own (e.g. "animal" -> a worm, a bird, a fish -- multiple emoji, not
 *   one). All three are knowingly looser than MEDIUM in *what counts as
 *   related enough* -- not in any notion of symbol ownership. No tier here
 *   ever checks whether an emoji is "already used" by some other word; only
 *   whether a *given* word has exactly one confident candidate. Two
 *   different words landing on the same emoji is normal and expected
 *   whenever they're close enough in meaning.
 *
 * All three tiers require the "Synonyms (English)" WordNet download for
 * anything past STRICT; see [com.japanglify.app.domain.emoji.EmojiAnnotator]
 * and `SqliteEmojiProvider`.
 */
enum class EmojiPrecisionTier(val id: String, val displayName: String) {
    STRICT(id = "strict", displayName = "Strict (exact match only)"),
    MEDIUM(id = "medium", displayName = "Medium (also matches real synonyms)"),
    LOOSE(id = "loose", displayName = "Loose (also matches related/similar words)");

    companion object {
        fun fromId(id: String?): EmojiPrecisionTier =
            entries.firstOrNull { it.id == id } ?: STRICT
    }
}
