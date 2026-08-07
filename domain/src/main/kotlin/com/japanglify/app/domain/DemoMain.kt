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
        val tokenizer = com.atilika.kuromoji.ipadic.Tokenizer.Builder().build()
        val loadMs = (System.nanoTime() - loadStart) / 1_000_000.0
        println("--- 0. Performance ---")
        println("Kuromoji dictionary load: %.1f ms".format(loadMs))

        val kuromojiProvider = JapaneseAnalyzer.ReadingProvider { raw ->
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
                JapaneseAnalyzer.SurfaceReading(t.surface, reading)
            }
        }
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
    }
}
