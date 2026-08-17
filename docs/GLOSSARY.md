# Glossary

Linguistic and project terms used across Japanglify — English and Japanese.
Where a term is Japanese, the native spelling and its romaji are given, then a
plain-language definition, then (where relevant) a note on how Japanglify uses
it. Cross-references point at the code that implements the concept.

> Japanglify turns selected Japanese text into readable **furigana** + **romaji**
> (+ optional English **gloss** and emoji). Most terms below are the vocabulary
> that pipeline is built on.

---

## The writing systems

**Kanji** — 漢字 (*kanji*)
Chinese-derived logographic characters, each carrying meaning and one or more
readings (e.g. 語, 本). A single kanji may be read as one or several **morae**.
In Japanglify, kanji are the characters that get a **furigana** reading placed
over them; kana already spell out their own sound and normally don't.

**Kana** — 仮名 (*kana*)
The two Japanese syllabic scripts, **hiragana** and **katakana**, in which each
character represents one **mora**. Collectively "kana."

**Hiragana** — 平仮名 (*hiragana*)
The cursive kana script (あ, い, う…), used for native words, grammatical
endings, particles, and **furigana**. Japanglify normalizes all kana to
hiragana internally before romanizing (see `KanaConverter.toHiragana`).

**Katakana** — 片仮名 (*katakana*)
The angular kana script (ア, イ, ウ…), used mainly for loanwords, onomatopoeia,
and emphasis. One-to-one with hiragana in sound.

**Rōmaji / romaji** — ローマ字 (*rōmaji*)
Japanese written in the Latin alphabet ("nihongo"). Which spelling you get
depends on the **romanization system**. Implemented in `Romanizer`.

---

## Ruby and reading aids

**Furigana** — 振り仮名 (*furigana*)
Small kana printed alongside (above, in horizontal text) a kanji to show its
reading — e.g. に·ほん·ご over 日本語. A specific, reading-only use of **ruby**.
Japanglify's furigana row is annotation-only: it shows readings for kanji, and
by default omits identity readings over text that is already kana ("furigana on
kanji only").

**Okurigana** — 送り仮名 (*okurigana*)
The trailing kana attached to a kanji stem to spell out inflection — the かしい
of 懐かしい, the い of 凄い. Because okurigana is already visible as kana, its
reading isn't repeated as furigana; Japanglify splits such a word into a kanji
cell (which gets furigana) plus an okurigana cell (which doesn't). See
`TripleScriptRenderer.splitKanjiFurigana`.

**Ruby**
The typographic term for small annotation text set alongside a base run
(furigana is the Japanese case). Japanglify's HTML output uses real `<ruby>`
markup; its plain-text output approximates ruby with aligned interlinear rows.

**Gloss**
A brief English meaning shown for a word or particle (e.g. 紙 → "paper"). Sourced
from a dictionary via `GlossAnnotator`; which meaning is chosen is the **sense**
question below.

---

## Sound and rhythm (phonology)

**Mora** — モーラ / 拍 (*mōra*)
Japanglify's atomic unit of Japanese sound — **the phoneme**: the smallest
indivisible sound the language builds words from, and the beat Japanese timing
counts. **In this project mora and phoneme are the same thing**, and the two
terms are used interchangeably. Each full kana is one mora/phoneme; a **yōon**
digraph (きゃ) is one; the **sokuon** (っ) and syllabic **ん** are each their
*own*. It is **not** a syllable — とうきょう (Tōkyō) is **4 morae** (to-o-kyo-o)
but reads as 2 syllables in English. The interlinear romaji marks each mora
boundary with a middle dot ("ni·hon·go"); see `Romanizer.romanizeMora`.

**Phoneme**
The atomic, indivisible unit of sound. In Japanglify this is exactly the
**mora** — the two are equivalent and used interchangeably throughout the code
and docs; every place the code says "mora" it means this phonemic atom.

**Diphthong**
A vowel that glides between two qualities within a single unit (English "coin,"
"how"). Japanese vowel runs (あい *ai*, おう *ou*) are **not** treated as one
gliding diphthong: each vowel is its own **mora/phoneme**, a separate sound
unit. That is why a run like おう romanizes as ō / ou (a lengthened or
sequenced vowel — see **long vowel** and **chōonpu**) rather than as a single
merged glide.

**Syllable**
The English/general-linguistics unit of a vowel nucleus plus optional
surrounding consonants. Contrast **mora**: Japanese timing counts morae, not
syllables, which is why the project deliberately renamed "syllable" concepts to
"mora" (`romajiMora`, `romanizeMora`).

**Sokuon** — 促音 (*sokuon*)
The "small tsu" っ/ッ marking a geminate (doubled) consonant — がっこう →
ga**k**kō. It occupies its own mora despite writing no vowel. Romanized by
doubling the following consonant (`Romanizer` handles っ specially).

**Yōon** — 拗音 (*yōon*)
A "contracted sound" written as a full kana plus a small ya/yu/yo — きゃ, しゅ,
ちょ. One mora, two characters. Stored as **digraphs** in `Romanizer`.

**Chōonpu** — 長音符 (*chōonpu*)
The katakana long-vowel bar ー that lengthens the preceding vowel (ラーメン →
rāmen). Japanglify renders it as a **macron** or doubled vowel depending on the
romanization system (`Romanizer.applyChoonpu`), *not* as a mora boundary.

**Long vowel** — 長音 (*chōon*)
A vowel held for two morae (おう, ー, ああ). Written in romaji as a macron (ō) or
doubled vowel (ou/oo) by system.

**Syllabic n** — 撥音 (*hatsuon*), the mora nasal ん
The moraic nasal ん/ン. Its own mora, and its romaji depends on what follows
(n / m / n'): しんぶん → shimbun (traditional Hepburn) vs shinbun (modified).
See `Romanizer.romanizeN`.

---

## Romanization systems

Different standards for spelling Japanese in Latin letters. Japanglify supports
all of the below (`RomanizationSystem`, `Romanizer`).

**Hepburn (modified)** — ヘボン式 (*Hebon-shiki*)
The most English-intuitive system and Japanglify's default: し→shi, ち→chi,
つ→tsu, じ→ji. "Modified" uses macrons for long vowels (Tōkyō) and n' before
vowels/y.

**Hepburn (traditional)**
Older Hepburn; notably m before b/m/p (shimbun).

**Kunrei-shiki** — 訓令式 (*Kunrei-shiki*)
The Cabinet-ordered system, more systematic to Japanese phonology: し→si, ち→ti,
つ→tu, しゃ→sya.

**Nihon-shiki** — 日本式 (*Nihon-shiki*)
The strictest one-kana-one-spelling system, preserving historical distinctions
Kunrei merges (ぢ→di, づ→du, を→wo).

**Wāpuro rōmaji** — ワープロローマ字 (*wāpuro rōmaji*)
"Word-processor romaji": the keystroke spellings used for kana input (し→shi or
si, ん→nn, small kana with a leading x). Portable ASCII, no macrons.

**Macron**
The bar over a long vowel (ā ī ū ē ō) used by Hepburn to mark **long vowels**.
Wāpuro and the plain-text-portable paths use doubled vowels instead.

---

## Dictionary and annotation

**Base form / dictionary form** — 辞書形 (*jisho-kei*)
The uninflected lookup form of a word — 行き's base form is 行く. Produced by the
tokenizer (Kuromoji `getBaseForm`) and used as the dictionary key
(`SurfaceReading.baseForm`).

**Particle** — 助詞 (*joshi*)
A grammatical function word marking a word's role — は, を, の, に…. Japanglify
treats particles as their own cells (they get word spacing but never start a
wrapped line) and normalizes their spoken readings (は→wa, へ→e, を→o).

**Part of speech (POS)**
A word's grammatical category (noun, verb, adjective, particle…). See
`PartOfSpeech`.

**Sense**
One distinct meaning of a headword. A dictionary entry (especially JMdict) often
lists several senses; すごい is "terrible/dreadful" **and** "amazing/great."
Choosing which sense to show is done by `SenseSelector` under a
**sense-selection preset** (Modern / Classical / Custom), because JMdict's own
sense order is editorial, not usage-frequency.

**JMdict / EDICT**
The open Japanese–English dictionary project (EDRDG) Japanglify's downloadable
dictionary is built from. Supplies headwords, readings, POS, and multiple
**senses** with English **glosses**.

**Kuromoji**
The open-source Japanese morphological analyzer/tokenizer used on-device to
split text into words and supply readings and **base forms**.

---

## Rendering and layout

**Interlinear**
A layout stacking aligned rows for one span of text — here furigana / base /
romaji / gloss, one column per word or kanji. Japanglify's main plain-text
format (`OutputFormat.INTERLINEAR`, `TripleScriptRenderer`).

**Triple-script**
Shorthand for showing the same text in three scripts at once — Japanese base +
kana furigana + Latin romaji — which the renderer is named for.

**Mora seam**
Japanglify-specific: the middle dot (·) inserted between two directly-abutting
segments whose romaji were each mora-separated independently, so "su·go·i" +
"de·su" don't fuse into a bogus "…ide…" mora. See `MORA_SEAM` in
`TripleScriptRenderer`.

**Elision marker**
Japanglify-specific: the small mark (default 〃 "ditto") stamped on an
interlinear row where a redundant line was dropped rather than shown blank. See
`ElisionMarker`.

**Digraph / monograph**
In `Romanizer`, a **digraph** is a two-kana unit mapped to one romaji reading
(きゃ → kya, a **yōon**); a **monograph** is a single kana's reading (か → ka).

**Tategaki** — 縦書き (*tategaki*)
Vertical Japanese writing (top-to-bottom, right-to-left). Japanglify has a
plain-text vertical hook (`WritingOrientation.VERTICAL`); full tategaki would
need a dedicated view.

**Halfwidth / fullwidth** — 半角 / 全角 (*hankaku* / *zenkaku*)
Legacy character-width classes: fullwidth CJK/kana occupy ~two monospace cells,
halfwidth Latin/kana ~one. Japanglify's column alignment budgets width in these
units (`displayWidth`, `cjkDisplayWidthUnits`).

---

## Platform / integration

**PROCESS_TEXT**
The Android intent that puts an app's action in the text-selection menu
("Japanglify" alongside Copy/Share). The ideal entry point — but some hosts
(e.g. X) don't offer it, which is why the **Copy assist** fallback exists.

**Copy assist**
Japanglify's accessibility-service path for apps without PROCESS_TEXT: it detects
Copy (and optionally Cut) and expands the clipboard text, showing a result
notification. Depends on the accessibility service / clipboard listener being
enabled.
