package com.japanglify.app.domain.polyglos

/**
 * Offline lemma-to-gloss tables from classpath polyglos TSV resources.
 * Missing entries soft-fail (null); expand never throws for unknowns.
 */
class BundledLexicon private constructor(
    private val byNormalized: Map<String, String>
) {
    fun gloss(lemma: String): String? {
        val direct = byNormalized[normalizeKey(lemma)]
        if (direct != null) return direct
        val stripped = stripMarks(lemma)
        if (stripped != lemma) return byNormalized[normalizeKey(stripped)]
        return null
    }

    companion object {
        fun loadGreek(): BundledLexicon = loadResource("/polyglos/greek_core.tsv")
        fun loadHebrew(): BundledLexicon = loadResource("/polyglos/hebrew_core.tsv")

        private fun loadResource(path: String): BundledLexicon {
            val stream = BundledLexicon::class.java.getResourceAsStream(path)
                ?: return BundledLexicon(emptyMap())
            val map = linkedMapOf<String, String>()
            stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val t = line.trim()
                    if (t.isEmpty() || t.startsWith("#")) continue
                    val parts = t.split(TAB)
                    if (parts.size < 2) continue
                    val lemma = parts[0].trim()
                    val g = parts[1].trim()
                    if (lemma.isEmpty() || g.isEmpty()) continue
                    map[normalizeKey(lemma)] = g
                    map.putIfAbsent(normalizeKey(stripMarks(lemma)), g)
                }
            }
            return BundledLexicon(map)
        }

        fun normalizeKey(s: String): String =
            s.trim().lowercase().replace(WHITESPACE, " ")

        fun stripMarks(s: String): String {
            val nfd = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            return buildString(nfd.length) {
                for (ch in nfd) {
                    val type = Character.getType(ch.code)
                    if (type != Character.NON_SPACING_MARK.toInt() &&
                        type != Character.COMBINING_SPACING_MARK.toInt() &&
                        type != Character.ENCLOSING_MARK.toInt()
                    ) append(ch)
                }
            }
        }

        private const val TAB = "\t"
        private val WHITESPACE = Regex("\\s+")
    }
}

