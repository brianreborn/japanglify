# Πολγζλοξ (polyglos) — Requirements for 1.0-RELEASE

**Status:** Draft for implementation  
**Product name:** Πολγζλοξ (ASCII slug / Maven coords: `polyglos`)  
**Predecessor:** Japanglify (`com.japanglify.app`)  
**Normative language:** RFC 2119 (`MUST`, `MUST NOT`, `SHOULD`, `MAY`)

This document is the acceptance contract for evolving Japanglify’s domain into a **polyglot triple-script core** while keeping the Android selection / share shell. Swarm Conductor and CI docs stay under `docs/swarm-conductor/` and `docs/japanglify/`; they are out of scope here except where 1.0-RELEASE gates mention them.

---

## 1. Goals

1. **Pluggable analyzers** for Japanese, Ancient Greek, Modern Greek, Ancient Hebrew, and Modern Hebrew.
2. **One generalized triple-script renderer** that consumes analyzer output as **base / phonetic / gloss** layers (today’s surface / furigana+romaji / gloss+emoji generalized).
3. **Offline dictionaries** sufficient for polytonic Greek and Hebrew with niqqud, plus Erasmian and SBL romanizations (and existing Japanese romaji systems).
4. **Script detection** so mixed or unknown selections route to the right analyzer(s) without a mandatory settings toggle for everyday use.
5. **Rename** the package/engine brand to Πολγζλοξ while **preserving** `ACTION_PROCESS_TEXT` and share-target **ingress**. Clipboard assist is **output only** (text / optional PNG after expand) — not an input path.
6. Ship as **`1.0-RELEASE`** (version naming must follow the project’s existing scheme; today that is SemVer + `versionCode` in Gradle).

## 2. Non-goals (1.0-RELEASE)

- Play Store listing / AAB Play publishing.
- Online translation as a required layer (existing optional Translate action MAY remain; it MUST NOT block offline expand).
- Full morphological parsers for Greek/Hebrew equal to Kuromoji’s Japanese depth (token+reading+gloss tables are enough for 1.0 if quality bars in §8 pass).
- Renaming the GitHub repository or swarm host IDs (MAY stay `japanglify` in URLs; user-visible strings SHOULD say Πολγζλοξ).
- New swarm-conductor features (use existing `/accept` `/uat` `/kick` flow).

## 3. Definitions

| Term | Meaning |
|---|---|
| **Base** | Original script graphemes as selected (kanji/kana, polytonic Greek, Hebrew with or without niqqud). |
| **Phonetic** | Reading layer: furigana and/or romaji for Japanese; Erasmian or SBL (and related) for Greek; a documented Hebrew romanization for Hebrew. |
| **Gloss** | Short target-language gloss (default English), optionally with emoji when the existing emoji pipeline applies. |
| **Analyzer** | Pure-JVM component: `CharSequence` + settings → `List<AnnotatedSegment>` (or successor type). |
| **Renderer** | Pure-JVM component: segments + settings → flat string and/or structured interlinear rows. |
| **Engine** | Facade: tokenize → per-token detect → analyze → (optional gloss/emoji) → render. |
| **Shell** | Android `app` module entry points that call the engine; no linguistics inside Activities/Services beyond wiring. |

---

## 4. Architecture requirements

### 4.1 Module boundary (unchanged)

- `domain/` MUST remain pure Kotlin/JVM with **zero** Android framework types.
- `app/` MUST depend on `domain`; `domain` MUST NOT depend on `app`.
- `./gradlew :domain:test` MUST pass on a host with JDK 17 and **no** Android SDK.

### 4.2 Pipeline

Analyze is **per whitespace/punctuation token**, not one analyzer for the whole selection.

```
PolyglosEngine.expand(text, settings)
  → WhitespaceTokenizer.tokenize(text)
  → for each token:
       if not a word → passthrough (base-only segment)
       else ScriptDetector.detect(token)     → ScriptProfile
            if profile.mixed                 → passthrough (mixed-script *word*)
            else AnalyzerRegistry.select(profile, forceAnalyzerId)
                 analyzer.annotate(token)    → segments  (or passthrough if none)
  → concatenate token segments
  → (optional) GlossAnnotator / EmojiAnnotator
  → TripleScriptRenderer.render(...)         → String
```

- Mixed **input** (several monolingual words in one selection) is this loop: each word is detected and routed independently. See §7.
- Document-level `ScriptDetector.detect(text)` remains available for diagnostics; it MUST NOT pick a single analyzer for the whole expand.
- The engine MUST keep the three entry points Japanglify has today: `expand`, `buildInterlinearRows`, `buildInterlinearDisplayRows` (names MAY be renamed with deprecation aliases through one minor if needed; 1.0-RELEASE SHOULD expose Πολγζλοξ names as primary).
- Analyzers MUST NOT call the renderer. Gloss/emoji enrichment MUST remain a post-analyze pass (as today) so languages without gloss dictionaries still render base+phonetic.

### 4.3 `AnnotatedSegment` generalization

`AnnotatedSegment` (or a clearly named successor) MUST support all 1.0 languages without Android-specific fields.

- **MUST** retain: `surface`, reading-for-base (today `furigana`), `romaji` / phonetic Latin, `gloss`, `emoji`, layout flags (`isBoundToPrevious`, `isParticle`, `isPhraseContinuation`, `romajiMora` or language-neutral equivalents).
- **SHOULD** add: `script` (enum/id of producing analyzer), `phoneticSystem` (e.g. `hepburn_modified`, `erasmian`, `sbl`, `hebrew_academic`).
- Japanese-specific names MAY remain as typealiases through 1.0 if migration cost is high; public docs MUST speak in base/phonetic/gloss.

### 4.4 Analyzer interface

```kotlin
interface Analyzer {
    val id: String                 // e.g. "ja", "grc-ancient", "el-modern", "hbo", "he-modern"
    fun supports(profile: ScriptProfile): Boolean
    fun annotate(text: CharSequence, settings: JapanglifySettings): List<AnnotatedSegment>
}
```

- Implementations MUST be side-effect free aside from reading bundled/offline assets.
- A `ReadingProvider`-style seam MUST remain for Japanese (Kuromoji) so tests can run without the dictionary JAR where applicable.

### 4.5 Renderer

- `TripleScriptRenderer` MUST accept segments from any 1.0 analyzer.
- Output formats in 1.0 MUST include at least: interlinear (default), parenthetical, furigana-inline analogue (language-neutral “reading brackets”), HTML ruby where applicable, compact brackets.
- Layout rules that are Japanese-specific (kana line-break, mora glue, okurigana split) MUST be gated on segment script or analyzer id so Greek/Hebrew do not inherit broken assumptions.
- Image / interlinear row builders used by clipboard PNG MUST keep working for Japanese and MUST produce readable columns for Greek/Hebrew (phonetic under/over base; gloss row optional).

---

## 5. Language / analyzer requirements

### 5.1 Japanese (`ja`) — preserve

- MUST preserve current Japanglify behavior for: Kuromoji readings, particle normalization, romaji systems already shipped, furigana-on-kanji defaults, gloss/emoji optional pipelines.
- Regression: existing `:domain:test` suite MUST remain green (rename-only failures fixed in the same change set).

### 5.2 Ancient Greek (`grc-ancient`)

- MUST tokenize on whitespace/punctuation with grapheme-safe handling of combining diacritics (polytonic).
- MUST expose phonetic Latin via **Erasmian** (default for ancient) and MUST support **SBL** as a selectable alternate.
- MUST strip or retain diacritics in phonetic output according to settings (default: retain vowel length / rough breathing contrasts required by the chosen scheme’s documented mapping).
- Gloss: offline lexicon lookup MUST cover a documented core NT/LXX frequent set for 1.0; uncovered tokens MUST render base+phonetic without failing the expand call.

### 5.3 Modern Greek (`el-modern`)

- MUST handle monotonic Greek as primary; polytonic input MUST NOT crash (MAY fall back to ancient mappings for diacritics).
- Phonetic: a documented modern scheme (MAY reuse a subset of SBL or a modern-phonemic table); settings MUST name it clearly distinct from Erasmian.

### 5.4 Ancient Hebrew (`hbo`)

- MUST accept consonants with optional **niqqud** and cantillation; cantillation MUST NOT break tokenization.
- Phonetic: documented academic romanization suitable for Biblical Hebrew (table in repo).
- Gloss: offline core lexicon (documented lemma list); missing entries soft-fail as in Greek.

### 5.5 Modern Hebrew (`he-modern`)

- MUST accept pointed and unpointed modern orthography.
- Unpointed text: phonetic MAY be best-effort or suppressed with settings; expand MUST still return base (and gloss if lexicon hits).
- MUST NOT require network.

---

## 6. Dictionaries & assets (offline)

| Asset | Purpose | 1.0 bar |
|---|---|---|
| Japanese (existing JMdict / emoji / WordNet paths) | Gloss + emoji | Keep downloadable + bundled flavors |
| Greek polytonic ↔ latin tables | Erasmian + SBL | Bundled in `domain` or `app` assets; no network |
| Greek gloss core | Ancient + modern glosses | Bundled SQLite/JSON; size SHOULD stay modest; document coverage |
| Hebrew niqqud / romanization tables | Phonetic | Bundled |
| Hebrew gloss core | Ancient + modern | Bundled; document coverage |

- License files MUST be listed in About / Credits (same pattern as Kuromoji/JMdict).
- Assets MUST work with airplane mode after first install for **bundled** flavor; downloadable flavor MAY omit heavy Japanese dicts as today but Greek/Hebrew 1.0 tables SHOULD be small enough to bundle even in downloadable builds.

---

## 7. Script detection

- `ScriptDetector` MUST classify a selection into a `ScriptProfile` using Unicode blocks / script runs (Hiragana, Katakana, Han, Greek, Hebrew, Common/Inherited).
- When a single dominant script is present, the matching analyzer MUST be selected automatically.
- Mixed **input** (one selection containing several languages as separate words) MUST be accepted. Each monolingual word is routed to its analyzer.
- Mixed-script **words** MUST NOT be interpreted. If characters from two or more of {Japanese, Greek, Hebrew} appear inside a single word (whitespace/punctuation-delimited token), that word MUST be emitted base-only — the same passthrough used for anything the engine does not grasp. MUST NOT pick a dominant script and force-romanize the foreign letters in that word.
- Clean concatenation of long script runs without spaces is a SHOULD for later; 1.0 MAY treat an unspaced mixed token as a mixed word (passthrough).
- Explicit user override in Settings MUST be available (`forceAnalyzer`: auto / ja / grc-ancient / el-modern / hbo / he-modern).
- Latin-only tokens MUST remain a no-op (passthrough; do not spam notifications). Clipboard is not an ingress — see §8.

---

## 8. Android shell requirements

- `ACTION_PROCESS_TEXT` MUST remain registered and functional.
- Share-target MUST keep calling the engine facade (renamed OK).
- Clipboard assist is **not** an ingress. Do not poll, observe, or auto-expand clipboard **input**. Clipboard **output** (plain text and optional interlinear PNG after expand) MUST remain.
- Accessibility selection chip MAY remain as a selection UX; it MUST NOT become clipboard surveillance.
- Application id: `com.japanglify.app` MAY remain for upgrade continuity through 1.0; if changed, an upgrade/migration plan MUST be documented. **Default for 1.0:** keep applicationId; change **label** / About strings to Πολγζλοξ.
- Kotlin/Java packages SHOULD migrate toward `com.polyglos…` (or `com.polyzlox…`) with thin `com.japanglify…` typealiases deprecated, OR keep packages and only rename user-visible brand — decision recorded in §12. **Preferred:** brand + engine type names first; full package rename MAY slip post-1.0 if it blocks release.
- Settings MUST expose phonetic system choices per script family without breaking existing Japanese preference keys (migrate with defaults).

### 8.1 Device UAT (QODESH Note9 validated path)

- Manual UAT MUST include Note9-class API 29 device via swarm-bench `/uat` or local adb:
  - Japanese Share and/or `PROCESS_TEXT` smoke (not clipboard poll).
  - Share/`PROCESS_TEXT` a polytonic Greek sample → phonetic visible.
  - Share/`PROCESS_TEXT` a niqqud Hebrew sample → phonetic visible.
- Debug iteration (adb install -r, force-stop, launch, logcat) MUST remain viable on the pool-bench host.

---

## 9. Packaging, version, rename checklist

| Item | 1.0 requirement |
|---|---|
| User-visible name | Πολγζλοξ (launcher label, PROCESS_TEXT label, notifications) |
| ASCII docs slug | `polyglos` |
| Engine type | `PolyglosEngine` (alias `JapanglifyEngine` optional) |
| Version | `1.0.0` release (not beta); `versionCode` bumped per project rules |
| RELNOTES / README | Describe polyglot scope + script detection |
| Light-ware / licenses | Preserve; add new dict licenses |

---

## 10. Testing requirements

- Unit tests in `domain` for:
  - Script detection fixtures (ja / el / grc / he / mixed / latin).
  - Each analyzer: golden strings → expected phonetic substring presence.
  - Renderer: does not throw on Greek/Hebrew segments; Japanese goldens still match.
  - Erasmian vs SBL switch changes output for a fixed polytonic word.
- No emulator required for `:domain:test`.
- App instrumentation MAY remain manual; swarm-bench UAT checklist in §8.1.

---

## 11. Acceptance criteria (exit for 1.0-RELEASE)

1. Engine + renderer + five analyzers land behind script detection with Settings override.
2. Offline Erasmian + SBL + Hebrew romanization tables + core gloss assets documented and loaded without network.
3. Japanese regression suite green; PROCESS_TEXT + Share still work on device. Clipboard is output-only.
4. README / RELNOTES describe Πολγζλοξ polyglot 1.0; About credits updated.
5. Tagged GitHub Release `1.0.0` (or project-equivalent) with downloadable APK(s), not a beta prerelease.
6. Open Japanglify issues #5/#6/#7 are **not** reopened as duplicates; only touched if required for the refactor.

---

## 12. Open decisions (resolve during implementation)

1. **applicationId** keep vs rename (default: keep).
2. **Package rename** depth for 1.0 vs brand-only (default: brand + engine types; packages follow if low-risk).
3. **Mixed-script** — **closed.** Mixed **input** (separate words) is accepted and routed per token (§4.2, §7). Mixed-script **words** pass through base-only. Not “dominant + passthrough for other runs.”
4. Exact **modern Greek** and **modern Hebrew** romanization table sources (cite in `docs/polyglos/SOURCES.md`).
5. Whether downloadable APK bundles Greek/Hebrew cores always (default: yes, if &lt; ~2 MiB combined compressed).

---


## 12a. Forward design (not in 1.0 implementation scope yet)

Voice input, unified ingress→expand→present cleanup, language-pack priority, and cold-start non-blocking behavior are specified in **[VOICE_AND_PIPELINE.md](./VOICE_AND_PIPELINE.md)**.

- That document is **normative for planning**.
- **Implementation is deferred** until higher-priority 1.0 work finishes (unless an item is explicitly pulled forward — cold-start mitigations MAY ship early).
- Do not treat voice as a parallel stack: it MUST feed the same re-render/`expand` path as Share and `PROCESS_TEXT`. Clipboard is output-only, not an ingress.

## 13. Document history

| Date | Change |
|---|---|
| 2026-09-21 | Closed mixed-input vs mixed-word (§7, §12.3); per-token pipeline (§4.2); clipboard assist is output-only, not ingress (§1.5, §8, §8.1, §11, §12a). |
| 2026-09-19 | Initial requirements from polyglot evolve brief + current Japanglify architecture (`JapaneseAnalyzer` → `TripleScriptRenderer` pipeline). |
