package com.japanglify.app.domain.emoji

/**
 * A small, hand-reviewed table of category words (e.g. "animal", "vehicle")
 * that have no single representative CLDR emoji of their own, mapped to a
 * short cluster of 2-3 emoji for their most central members instead (e.g.
 * "animal" -> a worm, a bird, a fish).
 *
 * This is deliberately NOT a general mechanism: it was built by generating
 * candidates from WordNet's hyponym tree (every word with a large enough
 * hyponym tree, descending up to 4 hops, requiring each candidate member's
 * *own* dominant sense to match the traversal path to rule out homograph
 * collisions, capped at 3 results ordered by tree-depth-then-corpus-
 * frequency so central/generic members rank above narrow specifics) and
 * then reviewing every single result by hand -- the automated pass alone
 * produced plenty of nonsense (e.g. "touch" -> a kiss, a bat, a hammer;
 * "material" -> wood, salt, a hyacinth flower) that no amount of tuning
 * fully eliminated, especially for abstract root words. Only words whose
 * *entire* candidate cluster read as sensible on inspection are here; nothing
 * partially-pruned or "mostly right" was kept. See EmojiAnnotator for where
 * this is consulted (LOOSE tier only, absolute last resort).
 */
object CategoryEmoji {
    val TABLE: Map<String, String> = mapOf(
        "person" to "🧑🧑‍🔬🧒",
        "individual" to "🧑🧑‍🔬🧒",
        "someone" to "🧑🧑‍🔬🧒",
        "car" to "🚗🚕🚑",
        "auto" to "🚗🚕🚑",
        "automobile" to "🚗🚕🚑",
        "motorcar" to "🚗🚕🚑",
        "container" to "✉🧺👛",
        "clothing" to "👗🧣🧥",
        "garment" to "👗🧣🧥",
        "medium" to "🎦📰📺",
        "bone" to "🦴🦷💀",
        "tissue" to "🦴🦷💀",
        "lamp" to "🕯🔦💡",
        "bird" to "🐦🦜🦉",
        "insect" to "🐛🪲🐜",
        "mammal" to "🐁🐒🐀",
        "organ" to "🪽👁👂",
        "building" to "🏨🏥🕍",
        "edifice" to "🏨🏥🕍",
        "animal" to "🪱🐦🐟",
        "beast" to "🪱🐦🐟",
        "creature" to "🪱🐦🐟",
        "tool" to "🪏🪛🪓",
        "vehicle" to "🚢✈🎈",
        "vertebrate" to "🐦🐟🐸",
        "worker" to "🧑‍✈🧑‍🏭🧑‍🔧",
        "entertainer" to "🧑‍🎤🃏🧑‍🩰",
        "performer" to "🧑‍🎤🃏🧑‍🩰",
        "defender" to "💂👮🕵",
        "guardian" to "💂👮🕵",
        "food" to "🍕🍣🥪",
        "nourishment" to "🍕🍣🥪",
        "nutrient" to "🍕🍣🥪",
        "sustenance" to "🍕🍣🥪"
    )
}
