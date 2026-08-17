# Release Notes

## v1.0 — Initial Release

Japanglify turns selected Japanese text into readable furigana + romaji
(+ optional English meanings and emoji) anywhere on your phone — no
copy-paste into a separate translator app required.

### Core conversion

- **System-wide text selection menu** — select Japanese text in any app,
  tap Japanglify, and the selection is expanded in place (editable
  fields) or copied (read-only text).
- **Share sheet target** — share text, or a whole web page URL, to
  Japanglify from any app; a shared URL is fetched and its text
  extracted for you to trim before converting.
- **Copy-assist fallback** — for apps that don't offer Japanglify in
  their selection menu (this varies by app, not a Japanglify bug),
  an optional Accessibility-based hook detects a plain copy and offers
  the converted result via notification instead.
- **Five output formats**: interlinear (furigana / base / romaji /
  gloss / emoji stacked lines), inline furigana (《ruby》-style),
  parenthetical (`漢字（かんじ / kanji）`), double-sided HTML ruby, and
  compact bracket notation.
- **Copy as image** — a rendered PNG of the converted text, pre-built
  in the background so it's ready the moment you tap "Copy image."

### Readings & romanization

- Kanji furigana readings via a bundled Kuromoji/IPADIC tokenizer —
  works fully offline, no download required for this part.
- Five romanization systems: Modified Hepburn, Traditional Hepburn,
  Kunrei-shiki, Nihon-shiki, and Wāpuro (IME-style).
- Configurable romaji position (above/below/before/after the base
  text) and capitalization.
- Mora-separated romaji option for interlinear output, with correct
  seam handling across word boundaries (すごいです → su·go·i·de·su, not
  a run-together su·go·ide·su).
- Adjustable interlinear line width, with a live preview across
  multiple font sizes so you can judge real-world wrapping.
- Elided-line markers (default: 〃) so a redundant or not-applicable
  line (e.g. romaji identical to an all-English line, or furigana on a
  kana-only word) reads as intentional, not as missing data.

### Dictionary: meanings & emoji

- Optional English word/particle glosses via a downloadable (or
  fully bundled, in the offline app variant) JMdict dictionary.
- **Tunable sense selection** — a word can have several dictionary
  meanings (e.g. すごい: "terrible" vs. "amazing"); choose Modern
  (favors richer, current-usage senses), Classical (favors older/
  archaic senses, for reading dated text), or fully custom weights.
- Optional emoji annotation matched from the resolved English meaning,
  with adjustable precision and part-of-speech scope.
- Resumable, cancelable dictionary downloads with mirror fallback if
  the primary host is slow or unreachable.
- A separate offline app build bundles the dictionaries directly in
  the APK for anyone who'd rather not download anything at all.

### Everything else

- No prompts on the menu action — every option lives on one settings
  screen, changes take effect immediately.
- Built-in diagnostics: why the selection-menu item might not appear
  in a given host app (platform behavior, not a Japanglify bug), a
  live copy-hook log, and a "Try it here" card that converts text
  without needing the system menu at all.
- Donate link and full license text, shown once on first launch and
  always available at the bottom of Settings after that.
