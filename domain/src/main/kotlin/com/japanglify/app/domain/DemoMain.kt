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

        val tokenizer by lazy { com.atilika.kuromoji.ipadic.Tokenizer.Builder().build() }
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
    }
}
