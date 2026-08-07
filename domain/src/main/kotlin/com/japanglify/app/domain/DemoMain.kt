package com.japanglify.app.domain

/**
 * Tiny CLI demo for the pure-JVM domain module (no Android).
 * Run: ./gradlew :domain:runDemo
 *      ./gradlew :domain:runDemo -PdemoText=ひらがな
 */
object DemoMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val text = args.firstOrNull() ?: "日本語を勉強する"

        // Kuromoji/IPADIC dictionary load — same library/dictionary Android
        // loads via KuromojiReadingProvider, so this JVM timing is
        // representative of the one-time cost JapanglifyApp.onCreate() pays
        // (see NOTES.md's performance-pass item).
        val loadStart = System.nanoTime()
        val kuromojiProvider = buildKuromojiProvider()
        val loadMs = (System.nanoTime() - loadStart) / 1_000_000.0
        println("--- 0. Performance ---")
        println("Kuromoji dictionary load: %.1f ms".format(loadMs))

        val analyzer = JapaneseAnalyzer(kuromojiProvider)

        val engine = JapanglifyEngine(analyzer)
        println("\n=== Japanglify Interactive Validation ===")
        println("Input Text: \"$text\"\n")
        
        println("--- 1. Romanization Systems (Interlinear Format) ---")
        for (sys in RomanizationSystem.entries) {
            val out = engine.expand(
                text,
                JapanglifySettings(
                    romanizationSystem = sys,
                    outputFormat = OutputFormat.INTERLINEAR
                )
            )
            println("[${sys.displayName}]:\n$out\n")
        }

        println("--- 2. Output Formats (Modified Hepburn) ---")
        for (fmt in OutputFormat.entries) {
            val out = engine.expand(
                text,
                JapanglifySettings(
                    romanizationSystem = RomanizationSystem.HEPBURN_MODIFIED,
                    outputFormat = fmt
                )
            )
            println("[${fmt.displayName}]:\n$out\n")
        }

        println("--- 3. Romaji Positions ---")
        for (pos in RomajiPosition.entries) {
            val out = engine.expand(
                text,
                JapanglifySettings(
                    romanizationSystem = RomanizationSystem.HEPBURN_MODIFIED,
                    outputFormat = OutputFormat.PARENTHETICAL,
                    romajiPosition = pos
                )
            )
            println("[${pos.displayName}]:\n$out\n")
        }

        // Long-selection cost: TripleScriptRenderer's interlinear path is
        // the most expensive (per-cell measurement + line-wrap packing) —
        // a repeated-sentence stand-in for a long real-world selection
        // (e.g. a paragraph pasted into Copy assist), see NOTES.md's
        // performance-pass item.
        println("--- 4. Performance: long-selection interlinear render ---")
        val longText = "日本語を勉強する。".repeat(200)
        println("Input length: ${longText.length} chars")
        val renderStart = System.nanoTime()
        val longOut = engine.expand(
            longText,
            JapanglifySettings(outputFormat = OutputFormat.INTERLINEAR)
        )
        val renderMs = (System.nanoTime() - renderStart) / 1_000_000.0
        println("Analyze + render: %.1f ms (%d output chars, %d rows)"
            .format(renderMs, longOut.length, longOut.split("\n\n").size))

        // Word/particle glosses — proves the tokenize -> base-form lookup ->
        // formatted-gloss pipeline against real Kuromoji tokenization, ahead
        // of any Android/SQLite/download code existing (see the dictionary
        // feature plan's Phase 2). No real dictionary is downloaded yet, so
        // this uses a tiny hand-built stand-in covering just this sentence's
        // words — same idea as the fake DictionaryProvider the unit tests use.
        println("\n--- 5. Word/particle glosses (fake dictionary, real Kuromoji tokens) ---")
        val fakeDictionary = mapOf(
            "日本語" to com.japanglify.app.domain.dictionary.DictionaryEntry(
                "日本語", "にほんご", com.japanglify.app.domain.dictionary.PartOfSpeech.NOUN, "Japanese"
            ),
            "を" to com.japanglify.app.domain.dictionary.DictionaryEntry(
                "を", null, com.japanglify.app.domain.dictionary.PartOfSpeech.PARTICLE, "object marker"
            ),
            "勉強" to com.japanglify.app.domain.dictionary.DictionaryEntry(
                "勉強", "べんきょう", com.japanglify.app.domain.dictionary.PartOfSpeech.NOUN, "study"
            ),
            "する" to com.japanglify.app.domain.dictionary.DictionaryEntry(
                "する", "する", com.japanglify.app.domain.dictionary.PartOfSpeech.VERB, "to do"
            )
        )
        val glossAnnotator = com.japanglify.app.domain.dictionary.GlossAnnotator(
            com.japanglify.app.domain.dictionary.GlossAnnotator.DictionaryProvider { key -> fakeDictionary[key] }
        )
        val glossAnalyzer = JapaneseAnalyzer(kuromojiProvider, glossAnnotator)
        val glossSegments = glossAnalyzer.annotate(text, JapanglifySettings(includeGlosses = true))
        for (seg in glossSegments) {
            println("  ${seg.surface.padEnd(6)} -> ${seg.gloss ?: "(no entry)"}")
        }

        // Rendering integration (Phase 3): same fake dictionary, run through
        // the real engine for every OutputFormat, so the actual gloss
        // markup/layout per format is visible here too, not just the raw
        // per-token lookups above.
        println("\n--- 6. Word/particle glosses rendered per output format ---")
        val glossEngine = JapanglifyEngine(glossAnalyzer)
        for (fmt in OutputFormat.entries) {
            val out = glossEngine.expand(
                text,
                JapanglifySettings(outputFormat = fmt, includeGlosses = true)
            )
            println("[${fmt.displayName}]:\n$out\n")
        }

        // Optional English→emoji annotation (emoji Phase 2): a second pass
        // layered on top of the gloss pipeline above. Fake CLDR-style
        // provider matching "Japanese" (illustrates the default -- a precise
        // match elides the now-redundant English word) -- real CLDR data
        // wouldn't actually match "study"/"to do" (verb infinitives and
        // plural/singular mismatches rarely hit CLDR's noun-skewed short
        // names; see the plan's precision-rule note), so only 日本語 gets an
        // emoji here, which is exactly the "fires where it makes sense"
        // scoping this feature was designed around.
        println("\n--- 7. Optional English→emoji annotation ---")
        val fakeEmoji = mapOf("japanese" to "🇯🇵")
        val emojiAnnotator = com.japanglify.app.domain.emoji.EmojiAnnotator(
            com.japanglify.app.domain.emoji.EmojiAnnotator.EmojiProvider { word, _ -> fakeEmoji[word] }
        )
        val emojiAnalyzer = JapaneseAnalyzer(kuromojiProvider, glossAnnotator, emojiAnnotator)
        println("[Default: precise match elides the English gloss word]")
        for (seg in emojiAnalyzer.annotate(text, JapanglifySettings(includeGlosses = true, includeEmoji = true))) {
            println("  ${seg.surface.padEnd(6)} -> gloss=${seg.gloss ?: "(none)"} emoji=${seg.emoji ?: "(none)"}")
        }
        println("[emojiAlwaysShowBoth = true: keeps the gloss alongside the emoji]")
        for (seg in emojiAnalyzer.annotate(
            text,
            JapanglifySettings(includeGlosses = true, includeEmoji = true, emojiAlwaysShowBoth = true)
        )) {
            println("  ${seg.surface.padEnd(6)} -> gloss=${seg.gloss ?: "(none)"} emoji=${seg.emoji ?: "(none)"}")
        }

        // Same rendering-integration proof as section 6, now with emoji on
        // top -- the actual markup/layout per format, not just raw lookups.
        println("\n--- 8. Emoji annotation rendered per output format (default: elides redundant English) ---")
        val emojiEngine = JapanglifyEngine(emojiAnalyzer)
        for (fmt in OutputFormat.entries) {
            val out = emojiEngine.expand(
                text,
                JapanglifySettings(outputFormat = fmt, includeGlosses = true, includeEmoji = true)
            )
            println("[${fmt.displayName}]:\n$out\n")
        }
    }

    /**
     * Real Kuromoji/IPADIC-backed [JapaneseAnalyzer.ReadingProvider] for the
     * plain JVM — mirrors the particle spoken-reading normalization the
     * app's Android-side `KuromojiReadingProvider` applies (は→ワ, へ→エ,
     * を→オ), without that class's Android-only number-token-merging
     * behavior. Shared with [RealDictionaryIntegrationTest], which runs the
     * full pipeline against real, organically-sourced Japanese rather than
     * this suite's usual hand-crafted `AnnotatedSegment` fixtures.
     */
    fun buildKuromojiProvider(): JapaneseAnalyzer.ReadingProvider {
        val tokenizer = com.atilika.kuromoji.ipadic.Tokenizer.Builder().build()
        return JapaneseAnalyzer.ReadingProvider { raw ->
            tokenizer.tokenize(raw).map { t ->
                val pos1 = t.partOfSpeechLevel1
                val reading = if (pos1 == "助詞") {
                    when (t.surface) {
                        "は" -> "ワ"
                        "へ" -> "エ"
                        "を" -> "オ"
                        else -> t.reading?.takeIf { it.isNotBlank() && it != "*" }
                    }
                } else {
                    t.reading?.takeIf { it.isNotBlank() && it != "*" }
                }
                JapaneseAnalyzer.SurfaceReading(t.surface, reading, baseForm = t.baseForm)
            }
        }
    }
}
