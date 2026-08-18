# Architecture

Two Gradle modules with a hard boundary between them:

```
domain/   pure Kotlin/JVM — text → triple-script conversion, zero Android deps
app/      Android shell — two independent ways to trigger `domain`, plus settings UI
```

`domain` has no dependency on `app`; `app` depends on `domain`. This is what
lets `./gradlew :domain:test :domain:runDemo` run on a machine with no
Android SDK at all (see README "Build without the Android SDK").

## `domain` — the conversion pipeline

Everything here is a pure function over strings, unit-testable on the JVM
(`domain/src/test`). One call chain does the whole job:

```
JapanglifyEngine.expand(text, settings)
  → JapaneseAnalyzer.annotate(text, settings)      → List<AnnotatedSegment>
  → TripleScriptRenderer.render(segments, settings) → String
```

`JapanglifyEngine` (`JapanglifyEngine.kt`) is a thin facade over that
two-stage pipeline. It has three entry points, all just annotate-then-render:
`expand()` returns the final flattened string (used by `PROCESS_TEXT` /
clipboard replace); `buildInterlinearRows()` / `buildInterlinearDisplayRows()`
stop short of flattening and hand back structured row/cell data instead, for
callers that need to lay out pixels or rich text themselves (the PNG
renderer, the in-app live preview) rather than re-parsing rendered text.

### Stage 1 — `JapaneseAnalyzer`: text → `AnnotatedSegment`s

Tokenizes raw text and produces one `AnnotatedSegment` per token, each
carrying `surface` (original chars), `furigana` (hiragana reading or null),
`romaji`, a mora-hyphenated `romajiSyllables` variant, and two morphology
flags: `isBoundToPrevious` (an auxiliary-verb/conjugation-ending token like
ました that completes the previous word rather than starting a new one) and
`isParticle` (は/を/の/に/…). Those two flags are opaque to the analyzer
itself — they arrive from the `ReadingProvider` — but they drive the
renderer's word-spacing and line-wrap rules downstream.

Tokenization is delegated through the `ReadingProvider` functional
interface (`fun tokenize(text): List<SurfaceReading>`), the one seam into
Android: `app` supplies `KuromojiReadingProvider`, a thin adapter over
Kuromoji/IPADIC morphological analysis that also normalizes particle
readings that are spelled one way and spoken another (は→ワ, へ→エ, を→オ)
and merges IPADIC's one-digit-per-token number fragmentation ("２５" → two
tokens) back into one. When no provider is supplied (or none is passed),
`JapaneseAnalyzer` falls back to a greedy character-class tokenizer (runs of
kana / kanji / punctuation / other) — kana-only text is still fully
annotated with zero dependencies; kanji just comes back without a reading.
Per token, the analyzer then decides:

- **furigana**: only for kanji-bearing surfaces with a resolved reading
  (`needsFurigana`), unless `furiganaKanjiOnly` is off, in which case
  kana-only spans also get an identity furigana line.
- **romaji**: computed via `Romanizer`, preferring the resolved reading;
  falling back to reading the surface's own kana directly for pure-kana
  tokens; falling back further to punctuation mapping or straight
  fullwidth→halfwidth passthrough (e.g. "Wi-Fi", a fullwidth-typed number)
  so the romaji line never has a silent gap under non-Japanese content.

### Stage 2 — `Romanizer` / `KanaConverter`: kana ↔ Latin

`Romanizer` walks hiragana (katakana is normalized first) one mora at a
time — using longest-match lookahead so yōon digraphs (きゃ, しゅ, …) are
recognized before falling back to single kana — through a per-mora lookup
table keyed by all 5 supported systems at once (`MoraRomaji`: modified
Hepburn / traditional Hepburn / Kunrei-shiki / Nihon-shiki / Wāpuro), so
adding or auditing a system-specific spelling is a one-line table edit. Three
rules get special handling because they aren't a 1:1 mora mapping:

- **Sokuon (っ)**: doubles the *following* mora's initial consonant
  (peeking ahead), not a fixed table entry.
- **Prolonged sound mark (ー)**: modifies the *previous* output in place —
  a macron for Hepburn (ō), doubling the vowel letter for Kunrei/Nihon, or
  a literal `-` for Wāpuro.
- **Syllabic ん**: system-dependent nasal assimilation with lookahead
  (traditional Hepburn: `m` before b/m/p, `n` elsewhere; modified Hepburn:
  `n'` before a vowel or y to disambiguate from a following mora, `n`
  otherwise).

A second entry point, `romanizeSyllables()`, produces the same output with a
middle-dot (·) inserted at each mora boundary (e.g. `ni·hon·go`) — used only
for the interlinear romaji row, since one unbroken run under a multi-kanji
word otherwise hides which part of the reading matches which kanji.
`KanaConverter` supplies the underlying character-class tests
(`isKana`/`isKanji`/`isPunctuation`/…), hiragana↔katakana conversion,
fullwidth↔halfwidth mapping, a punctuation→Latin table, and `morae()` — the
shared mora-splitting routine (handles yōon and treats っ/ん/ー as their own
mora) that both the romanizer and the renderer's kanji-splitting logic
depend on.

### Stage 3 — `TripleScriptRenderer`: segments → output string

Fans out to one of 5 `OutputFormat` renderers — `FURIGANA_INLINE` (Aozora
《》-bracket style, the most readable in plain-text chat), `PARENTHETICAL`
(漢字（かんじ / kanji）), `INTERLINEAR` (aligned three-line blocks, the
default), `HTML_RUBY` (furigana via `<ruby><rt>`; romaji/gloss as stacked block `<span>`s — not `<rb>`/`<rtc>`, which browsers no longer position), and
`COMPACT` (漢字〔かんじ〕[kanji]) — each honoring `RomajiPosition` (above /
below / before / after the base) independently. The interlinear path is
where most of the engine's complexity lives:

1. **Cell expansion** (`expandToFuriganaCells`) — turns each segment into
   one or more display cells. A kanji word with romaji disabled gets split
   one cell per kanji character via `splitKanjiFurigana`, which peels
   trailing okurigana by comparing surface/reading suffixes, then
   distributes the kanji-only reading's morae across the remaining kanji
   characters (`distributeMorae`) — remainder morae bias toward the *later*
   kanji, since on'yomi compounds more often lengthen toward the end
   (べん+きょう over 勉+強, not べんきょ+う). A syllabic ん is glued onto the
   *preceding* mora first (`glueSyllabicN`) so it never ends up donated to
   the wrong kanji. Punctuation becomes its own cell, bound to the previous
   word (never its own word-gap or wrap point), with its furigana-row
   rendering controlled by `FuriganaPunctuationStyle` (blank / repeat the
   mark / romanize it).
2. **Measurement** (`buildMeasuredRows`) — each cell's display width is
   measured in halfwidth units (`displayWidth`: fullwidth CJK/kana = 2,
   Latin/halfwidth = 1, based on Unicode block ranges) and cells are packed
   into rows against `maxLineWidthFullwidth`. Wrapping only ever happens
   before a cell whose `canWrapBefore` is true — sub-cells of a
   split kanji word and particles are excluded, so a wrap never stops
   mid-word or leaves a lone は/を/の dangling at a line start (matching
   Japanese typesetting convention).
3. **Formatting** — cells are padded to a shared column width (center-padded
   for normal cells, left-anchored for punctuation, since CJK fonts draw
   punctuation glyphs left-anchored in their em-square) using U+2800
   (Braille Pattern Blank) rather than a real space, because Discord and
   similar chat UIs strip leading/interior U+0020 and NBSP and would
   otherwise destroy the column alignment. Word Joiners (U+2060, zero-width
   non-breaking) are then inserted between every character of the finished
   line, because kana/CJK text permits a line break between any two
   characters by default (Unicode UAX #14) — without this, a host's own
   text view can still soft-wrap in the middle of a furigana cell after
   Japanglify already fit everything to `maxLineWidthFullwidth`.

`JapanglifySettings` is the one data class threaded through every stage —
an immutable snapshot of every user preference (romanization system, romaji
position, output format, furigana rules, line width, orientation). `domain`
never reads `SharedPreferences` itself; `app` loads settings once and passes
the snapshot in.

## `app` — two independent entry points into the engine

The Android app doesn't have one flow into `JapanglifyEngine`; it has two,
because Android's built-in text-selection hook (`PROCESS_TEXT`) simply isn't
offered by every host app (notably Discord/X use custom selection menus
that never call it). Both paths end up calling the same
`JapanglifyEngine.expand()`.

```
┌─ Path 1: PROCESS_TEXT (system selection-toolbar item) ─────────┐
│ Host app → floating text toolbar → "Japanglify"                │
│   → ProcessTextActivity (transparent, no UI)                   │
│   → engine.expand() → replace selection (editable) or          │
│                        write clipboard + toast (read-only)     │
└──────────────────────────────────────────────────────────────┘

┌─ Path 2: Copy-hook / clipboard assist (works everywhere) ──────┐
│ Host app → user selects text → taps Copy (or Cut)              │
│   → JapanglifyAccessibilityService observes the click/         │
│     clipboard change (a11y event) — OR —                       │
│     ClipboardAssistService polls ClipboardManager as a          │
│     foreground-service fallback when accessibility is off      │
│   → ClipboardProcessor.processClipboardIfNew()                 │
│   → engine.expand() → ClipboardNotifications.showResult()      │
│   → user taps a notification action, handled by                │
│     ClipboardAssistReceiver (copy text / copy image / replace  │
│     field in place / translate / pause hook)                   │
└──────────────────────────────────────────────────────────────┘
```

Path 2 is the one carrying most of the app's complexity, because it has to
reconstruct "the user just copied Japanese text" from accessibility signals
and a clipboard that Android increasingly restricts background access to —
there's no dedicated OS hook for it. Supporting pieces:

- **`JapanglifyAccessibilityService`** — the primary sensor. Watches
  `TYPE_VIEW_TEXT_SELECTION_CHANGED` (remembers the selection as a fallback
  for hosts that hide the clipboard from readers), `TYPE_VIEW_CLICKED`
  (detects Copy/Cut buttons by inspecting clicked node text), clipboard
  change events, and a backup poll loop (`pollRunnable`, since
  `OnPrimaryClipChangedListener` is unreliable on some OEM builds). Also
  owns `replaceFocusedField()` — writes a result back into the live focused
  node via `ACTION_SET_TEXT`, re-querying focus at click time rather than
  trusting anything captured earlier, since the user may have scrolled
  since.
- **`ClipboardAssistService`** — an optional foreground service offering
  the same clipboard-change detection without requiring the Accessibility
  permission (weaker: can't detect Cut, can't replace fields in place).
- **`ClipboardProcessor`** — the shared "read clipboard → run engine → show
  notification" pipeline both of the above call into. Also the gatekeeper
  for whether assist should run at all (`isAssistWanted`), whether Japanese
  is even present (`containsJapanese` — plain Latin text is a no-op, same
  as stock OS behavior), and length/duplicate/self-write filtering.
- **`LastResultStore`** — holds the most recent source/result pair and, more
  importantly, the anti-recursion state: every write Japanglify itself makes
  to the clipboard is tagged and remembered (ring buffer + a short
  time-based suppression window) so the Copy hook never re-processes its
  own output. This is the load-bearing piece that keeps paths 1 and 2 (and
  the notification's own "copy result" button) from looping into each
  other.
- **`ClipboardNotifications`** / **`ClipboardAssistReceiver`** — result and
  status notifications, and the `BroadcastReceiver` handling their action
  buttons (copy text, copy rendered image, replace field, open Translate,
  pause/resume the hook). Notifications are capped at 3 actions (Android
  silently drops a 4th), so the receiver picks which 3 make sense per host.
- **`ClipboardImageRenderer`** — rasterizes interlinear output to a PNG
  (via `TripleScriptRenderer`'s structured row data, not the flattened
  string) for hosts that mangle mixed CJK/Latin plain-text column alignment
  (Discord, X, Instagram — see `LastResultStore.IMAGE_PREFERRED_HOSTS`).
- **`SelectionActionOverlay`** — a secondary, optional floating chip shown
  on selection (`TYPE_ACCESSIBILITY_OVERLAY`), independent of the Copy
  hook.
- **`CopyHookDiagnostics`** — a small ring-buffer event log surfaced on the
  Settings screen for debugging why the hook did/didn't fire on a given
  host.

**Translation (`translate/`)** is the one exception to `app` otherwise being
as offline as `domain`: an opt-in, off-by-default `include_translation`
setting that appends a 4th English line via a free unofficial Google
Translate endpoint (`GoogleWebTranslator`, behind a `TranslationProvider`
seam mirroring `ReadingProvider`). It's wired as a best-effort enrichment
that arrives *after* the existing offline result at all three call sites —
the copy-hook notification updates in place once translation resolves, the
try-it card's explicit convert buttons do the same — except
`ProcessTextActivity`, which has no "update later" and instead bounds a
background-thread wait (~3s) before finishing either way. The setting
defaults off and every call site checks it before touching anything
translation-related, so this adds zero cost to the fastpath most users
never enable.

## UI layer

There's exactly one screen: `SettingsActivity` is a thin `AppCompatActivity`
shell (toolbar + edge-to-edge inset padding for API 35) around a single
`SettingsFragment`. It's also the app's launcher activity, so it's what
opens on first install and on tapping the icon — there is no separate
"home" screen, since the two conversion entry points (`PROCESS_TEXT`, the
copy hook) never need a UI of their own.

`SettingsFragment` is a `PreferenceFragmentCompat` built from
`res/xml/preferences.xml`. The whole screen — every toggle, the diagnostics
panel, and the live conversion sandbox — lives inside that one Preference
`RecyclerView`, in this order: **Scripts** (furigana/romaji toggles,
punctuation style) → **Clipboard** (copy-assist switch, accessibility
shortcut + status, foreground-service fallback toggle) → **Phoneticization**
(romanization system, romaji position) → **Rendering** (output format, line
width, orientation) → a **status card** → a **try-it card** →
**help/about**. Three custom `Preference` subclasses inflate real widget
layouts instead of a stock summary row, specifically so nothing needs a
second nested scroll container:

- **`StatusCardPreference`** — read-only diagnostics, refreshed on every
  `onResume()`: whether `ProcessTextActivity` is actually registered for
  `ACTION_PROCESS_TEXT` (queried live via `PackageManager.queryIntentActivities`,
  since OEM "optimizers" can silently disable it), whether the accessibility
  copy hook is running, and the last dozen lines from `CopyHookDiagnostics` —
  this is the mechanism for "why isn't Japanglify showing up / firing" support
  questions.
- **`TryItCardPreference`** — an in-app conversion sandbox: an editable text
  field, a live output pane, and two buttons ("Japanglify here" converts the
  current selection or the whole field in place; "Japanglify clipboard" reads
  the system clipboard, converts it, and writes the result back out through
  `LastResultStore`). This is also where shared text lands: `SettingsActivity`
  accepts `ACTION_SEND` (the "Share" menu item other apps offer) and passes
  the text into this card as a fallback for hosts with no `PROCESS_TEXT`
  support and no accessible copy button either.
- **`MaxLineWidthPreference`** — a slider and a numeric field kept in sync
  (a `syncing` flag guards against each one re-triggering the other's
  listener), rendering a live sample phrase at the candidate width on every
  drag so the effect of the wrap budget is visible without leaving Settings.

The try-it card's live preview (`SettingsFragment.updateLiveOutput`, debounced
180ms after each keystroke) is the one place true small-furigana rendering
happens: for `INTERLINEAR` output it calls `buildInterlinearDisplayRows`
(the role-tagged structured form) instead of the flat string renderer, and
applies a `RelativeSizeSpan` to shrink furigana lines to 62% size — everywhere
else in the app (clipboard, `PROCESS_TEXT` replacement) furigana is a
same-size plain-text row, since real small-ruby sizing only exists as a
Spannable, not in copyable text. Converting in place also has to cancel the
pending debounced preview refresh, since setting the field's text to its own
already-converted output would otherwise trigger the `TextWatcher` and
re-run the engine on the converted text a second time.

`PreferencesRepository` is the read/write bridge behind all of this: it
loads/saves `JapanglifySettings` against the default `SharedPreferences`
store, keyed by string constants shared between itself and the preference
XML/fragment. `JapanglifyApp` (the `Application` subclass) constructs the
single shared `PreferencesRepository` + `JapanglifyEngine` at process start,
so Kuromoji's dictionary loads once regardless of which entry point (this
UI, `PROCESS_TEXT`, or the copy hook) runs first.

## Workflow

End to end, from install to a converted result, the two engine-trigger paths
described above are really the only two workflows that matter day to day;
everything in the UI layer exists to configure and diagnose them:

1. Install the APK; first launch opens `SettingsActivity` (nothing else can —
   `ProcessTextActivity` and the copy-hook services have no launcher icon)
   and re-enables the `PROCESS_TEXT` component in case an OEM disabled it.
2. Configure scripts/phoneticization/rendering preferences once; optionally
   turn on Clipboard assist (prompts for the notification permission, then
   nudges the user to the system Accessibility settings screen to bind
   `JapanglifyAccessibilityService`, since Android requires that be granted
   outside the app).
3. Day-to-day use is entirely outside this app: select Japanese text in any
   other app and either tap **Japanglify** in the selection toolbar
   (`PROCESS_TEXT`, path 1) or just **Copy**/**Cut** as normal (the
   accessibility copy hook, path 2, for hosts like Discord/X that never
   expose custom `PROCESS_TEXT` items). Both converge on the same
   `JapanglifyEngine.expand()` call.
4. Returning to `SettingsActivity` is only needed to change settings, check
   `StatusCardPreference` when something isn't firing, or use the try-it
   card / Share-into-app fallback for a host that offers neither a
   `PROCESS_TEXT` item nor an interceptable Copy button.

Build system, multi-host support, and scripts are covered in the README —
not repeated here.

## Testing

`domain/src/test` unit-tests the pure pipeline (`JapaneseAnalyzerTest`,
`KanaConverterTest`, `RomanizerTest`, `TripleScriptRendererTest`) — no
emulator required. The `app` module has no automated test suite; its
`clipboard`/accessibility behavior is verified live on-device (see
`NOTES.md` for current manual-verification status and known gaps).
