# Πολγζλοξ dictionary / table sources (1.0)

Fill citations as assets land. Do not vendor copyrighted full lexicons without a compatible license.

## Planned / candidate

| Asset | Candidate source | License check |
|---|---|---|
| Erasmian mapping | Project table derived from public pedagogical charts | Document |
| SBL Greek | SBL Handbook of Style conventions (mapping table, not full handbook text) | Document |
| Greek gloss core | e.g. Dodson / Strong’s-derived public domain subsets, or AGFL-friendly lists | MUST verify |
| Hebrew romanization | Academic Biblical Hebrew conventions (SBL-like) | Document |
| Hebrew gloss core | Public-domain Strong’s / open lexeme lists | MUST verify |
| Japanese | Existing JMdict / Kuromoji / CLDR emoji / WordNet paths | Already credited |

## 1.0 facts (linguist 2026-09-21)

Durable behavior of the tables/code as shipped, not a full handbook.

### Mixed input vs mixed-script word

- `ScriptDetector.mixed` is true iff two or more of {Japanese, Greek, Hebrew}
  letter counts are > 0. Latin / Common / Other never make a word “mixed.”
- `PolyglosEngine.annotate` tokenizes first, then passthrough on `mixed`
  **before** analyzer select (including `forceAnalyzerId`).
- `GreekRomanizer` / `HebrewRomanizer` / `JapaneseAnalyzer` are not mixed-script
  gates; they will still map letters if called directly on a mash.

### Erasmian vs SBL (`GreekRomanizer`)

- Documented switch: υ → Erasmian `u`, SBL `y` (fixture: κύριος).
- Combining marks (accents, breathing) are stripped; rough breathing is not
  `h-` in either scheme.
- No diphthong or gamma-nasal rules. SBL ου is currently `oy` (letter-map
  υ=`y`), not handbook `ou`. Treat this as a known 1.0 table limit, not as
  the Erasmian/SBL test pair.

### Hebrew (`HebrewRomanizer` / `HebrewAnalyzer`)

- Academic consonant + common niqqud; cantillation skipped.
- Dagesh and shin/sin dots ignored (ב=`b`, ש=`š`).
- `he-modern` unpointed: phonetic suppressed unless `hebrewPhoneticUnpointed`.
- `hbo` romanizes the consonant skeleton even without niqqud.

### Proper names vs gloss/emoji (issue #7)

- Japanese wild gloss/emoji on names is Kuromoji `固有名詞` (keep reading,
  suppress gloss/emoji). Do not add a names dictionary in polyglos 1.0.
- Greek/Hebrew TSV rows for Ἰησοῦς, Χριστός, יְהוָה, מֹשֶׁה, יִשְׂרָאֵל are
  intentional lemma glosses, not that bug.
