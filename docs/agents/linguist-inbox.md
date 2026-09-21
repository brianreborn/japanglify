# Linguist inbox

Coders: add a `## YYYY-MM-DD` heading, the question, and the file/symbol.
Consultant: reply under that heading.

---

## 2026-09-21 — mixed input vs mixed-script words

**Requirement (user):** accept mixed input, but do not try to make sense of
multiple different languages' characters interspersed in a single word;
pass that through as we do for anything we don't grasp.

Please confirm the 1.0 rule in `REQUIREMENTS.md` §7 and flag any
romanizer/analyzer that still force-reads a mixed token. Idle scan after
that.

**Lock (conductor audit):** do not edit `PolyglosEngine.kt`,
`AnalyzerRegistry.kt`, `WhitespaceTokenizer.kt`, or `REQUIREMENTS.md`
this round — mixed-input code is already in the conductor working tree.
Reply here; new test file only if coverage is missing.

### Linguist reply (2026-09-21)

**§7 matches the engine.** `PolyglosEngine.annotate` is the 1.0 gate:

1. `WhitespaceTokenizer.tokenize` (NFC; combining marks stay on the letter).
2. Non-words (space / punctuation) → base-only.
3. `ScriptDetector.detect(token)` — `mixed` is true only when **two or more of
   {Japanese, Greek, Hebrew}** have letter counts > 0. Latin / Common / Other
   do **not** count as a second language in a word.
4. If `profile.mixed` → emit `AnnotatedSegment(surface)` only. This runs
   **before** `registry.select`, so `forceAnalyzerId` cannot force-romanize a
   mixed-script word.
5. Else select analyzer; `null` (Latin-only, digits, “other”) → same
   passthrough as unknown text.

User rule ↔ spec ↔ code:

| Input | Rule | Code |
|---|---|---|
| Mixed **input** (`λόγος 日本語 שָׁלוֹם`) | accept; each monolingual word to its analyzer | per-token detect + select |
| Mixed-script **word** (`日α語`, `λאγ`) | do not interpret; base-only | `profile.mixed` passthrough |
| Unspaced mixed run | 1.0 MAY treat as mixed word | same `mixed` flag |
| Latin-only (`hello`) | no-op / passthrough | `dominant == null` → no analyzer |

`REQUIREMENTS.md` §12.3 still says the mixed-script decision is open
(“dominant + passthrough”). §7 already closed it. Sync/conductor should
close §12.3; linguist did not edit REQUIREMENTS this round.

**Romanizers / analyzers if handed a mixed token directly** (engine never
does this for ja∩el∩he words): they **still force-read**. They are letter
maps / Kuromoji, not mixed-script-aware.

- `GreekRomanizer.romanize("日α語")` → `日a語` (α mapped, CJK kept).
- `HebrewRomanizer.romanize` maps Hebrew letters and appends anything else.
- `GreekAnalyzer` / `HebrewAnalyzer` tokenize then romanize every `isWord`
  token with no `profile.mixed` check. `supports()` is dominant-script only,
  so a mixed profile with Greek dominance would still claim the token.
- `JapaneseAnalyzer` fallback splits by kana/kanji/other; Greek/Hebrew
  letters are `OTHER` and get surface passthrough on the romaji line, not a
  refusal.

1.0 product behavior is therefore **engine-gated**. Do not “fix” mixed
routing by teaching the letter maps to guess; keep the gate in
`PolyglosEngine.annotate`. Optional later: a shared `if (detect.mixed)
passthrough` in each analyzer for defense in depth.

Existing engine coverage: `PolyglosSettingsRoutingTest`
(`mixedInputSeparateWordsEachLanguageAnalyzed`,
`mixedScriptWordIsPassthrough`, `unknownLatinWordIsPassthrough`). Added
`MixedScriptPassthroughTest` for he+el mash, `forceAnalyzerId` still
passthrough, three-word mixed input, and Latin+Greek in one token (Latin
is **not** a mix partner).

#### Idle scan

**Erasmian vs SBL** (`GreekRomanizer` letter tables; tested on κύριος):

- Intended 1.0 divergence: **υ** → Erasmian `u`, SBL `y`.
- Both **strip all combining marks** (accents + breathing) before mapping.
  Rough breathing is lost: ἑ → `e`, not `he`. Same for both schemes, so
  they do not diverge on it.
- No diphthong look-ahead. SBL **ου** is currently `o`+`y` = `oy`, not
  handbook `ou`. Erasmian ου luckily looks like `ou` because υ=`u`.
- No gamma-nasal (SBL γγ → ng). 1.0 is a pedagogical letter map, not a
  full SBL Handbook implementation. Do not treat `oy` vs `ou` as “Erasmian
  vs SBL” in tests; the κύριος pair is the documented switch.

**Unpointed Hebrew** (`HebrewAnalyzer`, `hebrewPhoneticUnpointed` default
false):

- `he-modern` + no niqqud (U+05B0–05BD, 05BF–05C7) → phonetic `null`;
  base (and gloss if lexicon hits) still returns. Matches REQUIREMENTS
  §5.5.
- `hbo` always romanizes the consonant skeleton (pointed or not).
- Shin/sin dots and dagesh are ignored in the table: ש is always `š`, ב
  always `b`. Cantillation (U+0591–05AF) is skipped in the romanizer and
  does not count as niqqud for the modern suppress path.

**Issue #7 proper-name glosses** (open:
[japanglify#7](https://github.com/brianreborn/japanglify/issues/7)):

- Japanese: Kuromoji/IPADIC `名詞,固有名詞` — keep readings/romaji, suppress
  implausible gloss/emoji. **No extra names dictionary.** Existing work on
  `agent/electrobrian-9-proper-names` / electrobrian#9 / PR 12. Polyglos
  1.0 core must not reopen a second implementation.
- Greek/Hebrew bundled TSV proper names (Ἰησοῦς, Χριστός, יְהוָה, מֹשֶׁה,
  יִשְׂרָאֵל) are **intentional lemmas**, not the Japanese POS bug. Emoji
  is not wired on those analyzers.

**Registry KDoc** still says “dominant script wins (passthrough for other
runs).” That is stale; mixed-word passthrough lives on the engine. Sync
owns the KDoc.

No engine/REQUIREMENTS edits. No voice/Settings.
