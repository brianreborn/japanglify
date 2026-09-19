package com.japanglify.app.domain.polyglos

import com.japanglify.app.domain.AnnotatedSegment
import com.japanglify.app.domain.JapaneseAnalyzer
import com.japanglify.app.domain.JapanglifySettings

/** Wraps existing [JapaneseAnalyzer] as polyglos analyzer `ja`. */
class JapaneseAnalyzerAdapter(
    private val delegate: JapaneseAnalyzer
) : Analyzer {
    override val id: String = AnalyzerIds.JA

    override fun supports(profile: ScriptProfile): Boolean =
        profile.dominant == ScriptKind.JAPANESE

    override fun annotate(text: CharSequence, settings: JapanglifySettings): List<AnnotatedSegment> =
        delegate.annotate(text.toString(), settings).map { seg ->
            if (seg.script != null) seg
            else seg.copy(script = id, phoneticSystem = settings.romanizationSystem.id)
        }
}
