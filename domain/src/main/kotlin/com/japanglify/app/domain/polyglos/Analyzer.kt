package com.japanglify.app.domain.polyglos

import com.japanglify.app.domain.AnnotatedSegment
import com.japanglify.app.domain.JapanglifySettings

/**
 * Pluggable language analyzer for Πολγζλοξ.
 * Pure JVM: CharSequence + settings → annotated segments.
 */
interface Analyzer {
    /** Stable id: "ja", "grc-ancient", "el-modern", "hbo", "he-modern". */
    val id: String

    fun supports(profile: ScriptProfile): Boolean

    fun annotate(text: CharSequence, settings: JapanglifySettings): List<AnnotatedSegment>
}
