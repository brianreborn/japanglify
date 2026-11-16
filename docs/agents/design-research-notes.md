# Design research notes

**Role:** eventual (2–5 year) product/system traps, ranked by how hard they
are to reverse. Not Gradle, adb, or this week’s hangs.

**Pass:** 2026-09-21 (kickoff stub replaced; pass 2 appends ingress
contract, SOURCES checklist, #13 brand, sibling skim). Docs only.

**Read:** `docs/polyglos/{REQUIREMENTS,VOICE_AND_PIPELINE,VOICE_CALL,SOURCES}.md`,
`ARCHITECTURE.md`, `AGENTS.md`, `DEVELOPMENT.md`, `docs/agents/*`, GitHub
issues **5, 6, 7, 13** and PRs **10–11** (`brianreborn/japanglify`).

---

## Authoritative 1.0 facts (this pass)

| Fact | Implication |
|---|---|
| Mixed **input** is OK | Separate words may be ja / Greek / Hebrew in one selection. |
| Mixed-script **words** pass through | A whitespace token mixing those scripts is base-only, like unknown text. Do not “make sense” of it. |
| Drop clipboard **INPUT** | Copy-hook / clipboard poll is not an ingress. |
| Keep clipboard **OUTPUT** | Finished expand may land on the clipboard (same as read-only PROCESS_TEXT). |
| Ingress | Share + `PROCESS_TEXT` + later live-call voice. |
| Devices | Note9 (API 29), 9185W (Android 15), GrapheneOS Pixel 8. |
| Languages that must work | `ja`, `grc-ancient`, `el-modern`, `hbo`, `he-modern`. |
| Package | ASCII slug `polyglos`; product **Πολγζλοξ**; `applicationId` `com.japanglify.app` stays for 1.0. |

REQUIREMENTS.md still lists clipboard-assist as a preserved **ingress** (§1.5,
§8, §11) and §12.3 still says “dominant + passthrough.” Treat those as stale
relative to the table above. Voice docs: `VOICE_CALL.md` is the call shape;
`VOICE_AND_PIPELINE.md` still talks clipboard ingress and P3 in-app mic — same
stale class. This notes file does not rewrite those contracts.

---

## Ranked reverse-hard risks

Irreversibility = how expensive it is to unwind after users, OS grants, paste
culture, and citations have formed around the 1.0 choice. Near-term bugs are
omitted even when they hurt.

### R1 — `applicationId` is the product’s legal name

**Lock now:** REQUIREMENTS §8 / §12.1 default: keep `com.japanglify.app`;
change label/About only. PR #11 does that.

**Why it gets worse:** Accessibility grants, PROCESS_TEXT ranking, notification
channels, backup/restore, “unknown source” install allow-lists, and any later
Obtainium/F-Droid/Play listing all key off the id. A 2028 rename is a new app
that orphans every GrapheneOS / Note9 install.

**1.0 decision:** Keep the id. Do **not** ship a second `com.polyglos…` APK
“just in case.” If a post-1.0 rename ever happens, it needs an explicit
migration app — not a Gradle one-liner.

**Do not:** treat Kotlin package moves (`com.japanglify` → `com.polyglos`) as
the same as applicationId. Packages can migrate; the id cannot, cheaply.

### R2 — Accessibility copy-hook as the soul of the product

**Lock now:** Drop clipboard **input**. Chip (issue #5) may remain as
**selection UX**, never as clipboard surveillance. GrapheneOS Pixel 8 is in
the device pool.

**Why it gets worse:** Japanglify’s architecture (and `AGENTS.md` /
`DEVELOPMENT.md` hierarchy) still ranks the a11y chip first and copy
observation as fallback, because Discord/X never offer PROCESS_TEXT.
Android’s accessibility attestation, GrapheneOS exploit notifications, and
“this app can read the screen” dialogs will only get harsher. An app that
needs **full control of the device** to read Japanese in Discord will never
be a trusted GrapheneOS assistant, no matter how pretty the logo (issue #13).

If 1.0 ships with Copy assist still on the first Settings screen and still
the documented “works everywhere” path, that is the brand for five years:
a clipboard sniffer with extra languages.

**1.0 decision:** Ingress = Share + PROCESS_TEXT + live-call voice. Chip is
optional selection chrome. Copy-hook / `ClipboardAssistService` poll is
legacy, visually subordinate, then gone. Do not replace clipboard input with
`AssistStructure` / screenshot scraping (see R3).

**Closed-host hole:** Discord/X still will not grow PROCESS_TEXT. The
non-surveillance answers are Share, an explicit chip on **this** selection
(not Copy), or the user speaking a call. If both clipboard-input **and** the
chip die, those hosts disappear. That is a product choice, not a bug.

### R3 — Exporting a digital assistant is a one-way reputation bet

**Lock now:** `VOICE_CALL.md` — do not register `VoiceInteractionService`
until a voice milestone; do not require `ROLE_ASSISTANT`; first call is the
user’s **own speech**, not what’s on screen.

**Why it gets worse:** Once Πολγζλοξ is an assistant candidate:

- GrapheneOS users who grant Digital assistant expect a privacy contract.
  Adding `onHandleAssist` / screenshot later is a bait-and-switch.
- Note9/Bixby: fighting the OEM slot trains “this app is broken” if the
  call only works as default assistant.
- Un-exporting `BIND_VOICE_INTERACTION` after F-Droid/Graphene listings
  cite “can be default assistant” is a store/review event.

**1.0 decision:** Protocol types + in-app host first (P3a). Exported
service + optional role prompt is P4, off by default, speech-only.
GrapheneOS MVP is **app `RecognitionService` + on-device model**, not
platform `SpeechRecognizer` (there is often no Google recognizer).
Online ASR stays explicit opt-in, default OFF — GrapheneOS Network
permission can deny even that.

### R4 — Default analyzer is a school of reading

**Lock now:** Auto Greek → `grc-ancient` (Erasmian default); auto Hebrew →
`hbo` (academic BH). Modern ids exist via `forceAnalyzerId`. Tables are
small and bundled.

**Why it gets worse:** Students and liturgy groups will quote “Πολγζλοξ
says…” as if it were a lexicon. Changing Erasmian→SBL defaults, or
ancient→modern auto, in 2029 is a silent rewrite of every saved habit and
screenshot. Unpointed modern Hebrew with a Biblical table will look
authoritative and be wrong.

`SOURCES.md` is still “planned / candidate.” Shipping 1.0 without named
tables means the first mapping that lands **is** the standard.

**1.0 decision:** Name the scheme in the phonetic layer (`phoneticSystem`
on segments) and in Settings (already: `greek_romanization`,
`hebrew_romanization`). Auto-ancient is fine if the UI says “Ancient
(Erasmian)” not “Greek.” Cite tables in `SOURCES.md` before 1.0-RELEASE
acceptance §11.2. Do not silently “improve” defaults after people learn
them.

### R5 — Gloss/emoji ethics at five-language scale (issue #7)

**Lock now:** Issue #7: Japanese proper names get wild common-word glosses
/ emoji. Fork approach: IPADIC `固有名詞` → keep reading, suppress
implausible gloss/emoji. **No extra names dictionary.** Gloss/emoji stay
experimental and off-by-default in the engine; README still markets them.

**Why it gets worse:** The same pipeline on Greek/Hebrew core gloss TSVs
(Strong’s / Dodson-class) will emoji-tag θεός, YHWH, and every personal
name in a NT/LXX sample. That is not a cute JA bug; it is a
religious/linguistic ethics problem. Once screenshots circulate, “the
app put a smiley on the Name” is the product.

IPADIC’s proper-noun tag does not exist for `grc-ancient` / `hbo`.
Copying the JA heuristic is a false safety.

**1.0 decision:** Gloss/emoji remain opt-in. Unknown / name-like tokens
soft-fail to base+phonetic. Do not enable emoji on Greek/Hebrew packs
until there is a **per-analyzer** suppress rule, not a JA POS check.
REQUIREMENTS §11.6 (don’t reopen #5/#6/#7 as duplicates) still applies:
fix the JA case, don’t pretend it generalizes.

### R6 — The renderer is a Japanese column engine wearing a polyglot coat

**Lock now:** `TripleScriptRenderer` measures CJK displayWidth, pads with
U+2800, inserts U+2060, splits okurigana, glues mora. REQUIREMENTS §4.5
says Japanese layout MUST be gated on script. Hebrew has **no** RTL/bidi
path in domain today. Clipboard PNG is the Discord/X paste format.

**Why it gets worse:** Interlinear paste culture (Braille blanks, word
joiners) will ossify. Hebrew (and any later Arabic) forced through LTR
columns will look like broken chat art. Fixing bidi later invalidates
every PNG width assumption and every “Copy image” habit. Combining
polytonic Greek on a mora-oriented cell splitter is a milder version of
the same trap.

**1.0 decision:** Gate wrap/glue/okurigana on analyzer id **now**, even
if Hebrew is still LTR-in-a-box. Do not let Greek/Hebrew inherit
`isParticle` / mora hyphenation. Record RTL as a post-1.0 renderer
milestone, not an accident of “TripleScript already works.” PNG UAT on
Note9 must include a niqqud line, not only 勉強.

### R7 — PROCESS_TEXT replace-in-place mutates the host document

**Lock now:** Editable hosts get the expanded string **in the field**.
Read-only hosts get clipboard output. Hebrew/Greek interlinear is much
larger than the source span.

**Why it gets worse:** Replacing a Biblical Hebrew selection with three
LTR lines inside an RTL editor is data loss. Users will undo-spam or
lose the source. Five years of “tap Πολγζλοξ to expand in place” cannot
be walked back without a Settings split (replace vs copy-out) that
nobody will find (issue #13 cruft).

**1.0 decision:** Prefer copy-out / overlay / notification for
Greek/Hebrew (and any RTL), or an explicit “replace field” action — not
silent replace. Japanese in-place can stay; it is the old contract.
Do not make voice finals replace the focused field.

### R8 — Whitespace is the language boundary

**Lock now:** Engine tokenizes, mixed-script **words** pass through,
monolingual words route. Unspaced mixed runs are SHOULD-later
(REQUIREMENTS §7). Tests already encode this.

**Why it gets worse:** Japanese and unpointed Hebrew are not
whitespace-languages in the Greek sense. A 1.0 mental model of “word =
spaces” will fight CJK study notes, LXX-with-Japanese glosses, and
voice transcripts that lack punctuation. Building a later morphological
splitter **under** passthrough is possible; building it **over** a
dominant-script force-romanizer would have been irreversible (the old
§12.3 default). Passthrough was the reversible choice. Keep it.

**1.0 decision:** Do not split mixed tokens to be helpful. Do not let
romanizers run if called on a mixed token from a future ingress that
skips the engine. Voice finals are just text: same rule. Language-pack
priority (VOICE_AND_PIPELINE §7) must not override mixed-word
passthrough.

### R9 — Brand, logo, and the overflow-menu name (issue #13)

**Lock now:** User-visible name SHOULD be Πολγζλοξ; GitHub/swarm ids MAY
stay `japanglify`. Issue #13: launcher still looks Japan-only; Settings
is a debug bench after Polyglos prefs. PR #10 used the misspelled slug
`polyzylox`; PR #11 corrected to `polyglos`.

**Why it gets worse:** PROCESS_TEXT labels are muscle memory. A Japan
flag / 字 glyph on GrapheneOS Pixel 8 tells Hebrew/Greek users they
installed the wrong app. Dual names (Japanglify in the toolbar,
Πολγζλοξ on the launcher) train support load for five years. Adaptive
icon + notification + tiny PROCESS_TEXT affordance all have to work;
a clever triple-script mark that dies at 24px is wasted.

**1.0 decision:** One user-visible name everywhere that is not a URL.
Logo: triple-script, readable small, GrapheneOS/legacy adaptive (issue
#13). ASCII slug stays `polyglos` — do not revive `polyzylox`. Repo
rename is a non-goal and should stay one.

### R10 — Empty provenance is a license time bomb

**Lock now:** SOURCES.md is a checklist, not citations. Japanese path
(JMdict / Kuromoji / CLDR / WordNet) is credited. Greek/Hebrew cores
are “MUST verify.” Light-ware About screen is the 1.0 home for licenses.

**Why it gets worse:** Sideload + later F-Droid/Graphene app stores will
ask. A bundled Strong’s-derived TSV without a recorded license is
un-removable once it has been in tagged 1.0 APKs (mirrors, Archive.org,
user backups). Replacing tables later also hits R4 (the reading
changes).

**1.0 decision:** No 1.0-RELEASE until SOURCES has a row per shipped
asset with license + subset rule. Prefer public-domain / CC that allows
bundling. Do not vendor a handbook.

### R11 — Clipboard **output** is still an OS-fragile presenter

**Lock now:** Keep output; drop input. Read-only PROCESS_TEXT already
writes the clipboard. Voice finals MAY too.

**Why it gets worse:** GrapheneOS clipboard access notifications,
Android’s background-clipboard lockdown, and OEM “intelligent”
clipboards will keep treating **writes** as hostile too. If the only
way to get a result out of Share/voice is a clipboard clobber, 2028
users will call it data loss (it overwrote what they were about to
paste). Discord still wants PNG (issue #6’s “nice view” impulse).

**1.0 decision:** Clipboard output is allowed, not required. Presenters
need at least one non-clipboard path that survives GrapheneOS (result
notification, overlay, in-call text, Share-back). Never write **partials**.
Never poll the clipboard “to see if present worked.”

### R12 — Settings-as-home cannot absorb language packs + voice (issue #13)

**Lock now:** One `SettingsActivity` / PreferenceFragment is the launcher.
Polyglos keys (`force_analyzer_id`, romanization, unpointed Hebrew) landed
on the same wall as Try-it, diagnostics, and Copy assist. Issue #13:
don’t invent new keys unless a sub-screen needs them. SharedPreferences
live under the kept applicationId forever.

**Why it gets worse:** Voice language packs (enable / priority / ASR
locale) plus per-script phonetics plus remaining JA furigana controls
will not fit a 2019 preference XML. Key names from the Japanglify era
cannot be renamed without migration. Issue #6 (live magnifier window)
is a **second primary UI**; if it is nailed onto Settings or the chip,
both stay crufty.

**1.0 decision:** Visual grouping now (issue #13): Polyglos first-class,
Try-it/status not the hero, Copy assist demoted. Stable preference keys.
Language packs and voice stay **out** of 1.0 XML. Issue #6 is design
exploration, not a 1.0 surface — a magnifier that is also a selection
chip that is also a call UI is three products.

### R13 — Note9 as eternal minSdk vs GrapheneOS as the real future DUT

**Lock now:** `minSdk 26`, Note9 required UAT, GrapheneOS Pixel 8 in
pool, 9185W wireless. Voice APIs that exist on 15 often have **no
implementation** on GrapheneOS and **no API** on Note9.

**Why it gets worse:** Designing voice “to the intersection” (classic
`SpeechRecognizer` + restart-on-final) makes GrapheneOS a guaranteed
failure unless an app-owned model ships. Designing only for Pixel 8
abandons the owner’s daily Note9. Keeping Note9 in the pool for five
years is a gift until it becomes a veto on every assist API.

**1.0 decision:** UAT matrix is three devices, three answers (see
`VOICE_CALL.md` §4), not one code path. Note9 does not block bundling
a recognizer for GrapheneOS. Raising minSdk is a **dated** decision
when Note9 leaves the pool — record it; don’t let API 29 freeze the
assistant story.

### R14 — Kuromoji/IPADIC flags are baked into layout

**Lock now:** `isBoundToPrevious` / `isParticle` / mora from
`ReadingProvider` drive wrap. JapaneseAnalyzer is the only deep
morphology. Greek/Hebrew are whitespace + tables (REQUIREMENTS
non-goal: no Kuromoji-depth parsers).

**Why it gets worse:** Replacing IPADIC (license, quality, names — #7)
requires a renderer that no longer believes those flags. A 1.0
“generalized” segment type that still uses furigana field names is
survivable; a 1.0 that teaches the renderer “particle = Japanese
only” and then later adds Hebrew clitics as particles will mis-wrap
for years.

**1.0 decision:** Keep Japanese flags Japanese. Don’t reuse them as
cute booleans for other languages. ReadingProvider seam stays the
swap point.

---

## Issue / PR mapping (eventual, not status)

| Item | Eventual stake |
|---|---|
| **#5** chip persistence | Last non-clipboard path into closed hosts. Persist as **selection**, or admit Discord is Share-only. Not a reason to keep copy-hook. |
| **#6** live magnifier / moveable window | Competes with Settings-as-home and with the voice call UI. Defer as a presenter, not a third ingress. |
| **#7** proper-name gloss/emoji | JA instance of R5. Must not be “fixed” by a names dictionary that then gets applied to scripture. |
| **#13** logo + Settings cruft | R9 + R12. First-class Polyglos chrome; Japan-only mark is a 5-year lie. |
| **PR #10** requirements docs | Contract started as `polyzylox`; slug is `polyglos`. Still lists clipboard ingress — drift vs this pass. |
| **PR #11** polyglos core | Right engine shape (pluggable analyzers, `PolyglosEngine`, offline tables). Settings keys landed. Voice still design-only. Don’t let core success freeze R1–R3. |

---

## What this pass is not saying

- Do not revive clipboard input.
- Do not implement VoiceInteractionService in 1.0.
- Do not rename applicationId for 1.0.
- Do not split mixed-script words to be clever.
- Do not treat Play Store as a 1.0 goal (still a 5-year distribution
  question: sideload + GrapheneOS is coherent; empty SOURCES is not).

---

## 2026-09-21 — pass 2

R2+R3, R4, and issue #13 turned into ship-time artifacts. Sibling
worktrees skimmed; not merged.

### 1.0 ingress contract (from R2 + R3)

One page. This is the GrapheneOS-safe shape. Clipboard **output** is a
presenter, not a way in.

| Role | Channel | 1.0 |
|---|---|---|
| **Ingress** | Share (`ACTION_SEND` `text/plain`) | Required. `ShareTargetActivity`. |
| **Ingress** | `ACTION_PROCESS_TEXT` | Required. Selection toolbar; editable replace is Japanese-era (see R7 for Greek/Hebrew). |
| **Ingress** | Live-call voice | Protocol only until an explicit voice milestone. Beats: start / partials / final / hang up. **Finals** (non-blank) → same `PolyglosEngine.expand(text)` as Share / PROCESS_TEXT. Partials never expand, never notify, never touch the clipboard. |
| **Ingress (optional)** | Selection chip (issue #5) | MAY stay as **this-selection** chrome. MUST NOT observe Copy/Cut, MUST NOT poll `ClipboardManager`, MUST NOT read other apps’ text except the selection the user is pointing at. |
| **Not ingress** | Clipboard assist / copy-hook / FGS poll | Removed. Do not put `CLIPBOARD` on `IngressSource`. |
| **Presenter** | Clipboard **output** | Allowed after a finished expand (text / optional PNG). Never required; never for partials. |
| **Presenter** | Notification / overlay / in-call text / Share-back | Need at least one non-clipboard path (R11). |
| **Not a product** | Try-it card | Host of the same expand path, not a fourth ingress. |

**GrapheneOS Pixel 8 — MUST NEVER grant at 1.0**

These are the three grants that freeze the app as a surveillance
assistant. Do not prompt, do not pre-check, do not “just for Discord.”

1. **Full accessibility copy-hook.** The system dialog is “full control
   of the device.” That is the Copy assist / `JapanglifyAccessibilityService`
   clipboard path. 1.0 MUST NOT require it for expand. If the optional
   chip still needs overlay permission, that is a **narrow** a11y use
   (draw over **this** selection). It is not an excuse to keep
   `ClipboardAssistService` or copy-event listeners. GrapheneOS users
   who deny a11y MUST still Share and PROCESS_TEXT.
2. **`ROLE_ASSISTANT` on by default.** Do not register
   `VoiceInteractionService` / `BIND_VOICE_INTERACTION` in the 1.0
   manifest. Do not auto-prompt Digital assistant. In-app call host
   (when voice is unblocked) MUST work with Bixby/none holding the
   slot. Optional role offer is a later Settings action after the
   service actually exists (voice P4).
3. **Screenshot / `AssistStructure` assist.** `onHandleAssist` /
   `onHandleScreenshot` are out. The call is the user’s **own speech**,
   not the screen. GrapheneOS hardened profiles may deny screenshots
   anyway; scraping would also reintroduce R2 under a friendlier name.

**Note9 / 9185W:** same contract. Note9 cannot be the default assistant
anyway (Bixby). Voice, if present, is the call protocol + classic
`SpeechRecognizer` or an app model — not a11y-as-mic, not clipboard
as voice.

**Closed hosts (Discord/X):** no PROCESS_TEXT. After clipboard-input
death, remaining paths are Share, optional selection chip, or speaking
the call. Losing the chip without Share-education is how those hosts
disappear (R2). Copy-hook is not the answer.

### SOURCES naming checklist (from R4)

`docs/polyglos/SOURCES.md` is still “planned / candidate.” Linguist
(uncommitted in their worktree) added **behavior** facts (υ `u`/`y`,
no diphthongs, unpointed `he-modern`) — not citations. This checklist
is what MUST be **named** in that file before 1.0-RELEASE (§11.2, §12.4).
Do **not** invent a handbook citation. If the row is a project letter
map, say so.

Japanese About credits already in `pref_about_credits_summary` (copy
them into SOURCES; do not re-derive):

- Kuromoji/IPADIC — Apache License 2.0
- JMdict/EDICT (EDRDG) — CC BY-SA 4.0
- Unicode CLDR emoji names — Unicode-3.0
- Princeton WordNet 3.0 — WordNet License

| MUST name before ship | In-tree today | Gap (do not fill with a guess) |
|---|---|---|
| **Erasmian** letter map (`GreekRomanizer.ERASMIAN`, Settings “Erasmian (ancient default)”) | KDoc: letter-level; SOURCES candidate: “public pedagogical charts” | **No chart, edition, or author named.** MUST say “project table” until a real source is attached. |
| **SBL** letter map (`GreekRomanization.SBL`, Settings “SBL academic”) | Documented 1.0 switch is υ → `y` vs Erasmian `u` (κύριος). Linguist: combining marks stripped (no rough-breathing `h-`); no diphthong/gamma-nasal; SBL ου is `oy`, not handbook `ou`. | **MUST NOT claim “SBL Handbook of Style.”** MUST name it as a **project letter map** using SBL-like letter choices, with the ου/`oy` limit written down. Actual handbook mapping: **gap**. |
| **Modern phonemic Greek** (`MODERN_PHONEMIC`) | Enum exists; REQUIREMENTS §12.4 still open | **No table source named.** Gap. |
| **Hebrew academic** (`HebrewRomanization.ACADEMIC`, `HebrewRomanizer`) | KDoc: “consonants + common niqqud”; cantillation skipped; dagesh/shin-dot ignored (ב=`b`, ש=`š`); `hbo` romanizes unpointed skeleton | **No SBL/academic handbook named.** Candidate line is “SBL-like.” Gap. |
| **Modern Hebrew phonetic** (`hebrewPhoneticUnpointed`) | Unpointed `he-modern` suppresses phonetic unless the setting is on | **No modern-Israeli scheme named.** Gap (§12.4). |
| **`greek_core.tsv`** | Resource SOURCES: “curated frequent NT/LXX + everyday modern lemmas”; rows include λόγος, θεός, Ἰησοῦς, Χριστός | **Not cited as Dodson, Strong’s, or any list.** MUST stay “project subset” until a license-compatible source is recorded. Inventing “Dodson” here would be a false credit (R10). |
| **`hebrew_core.tsv`** | “curated Biblical + everyday modern lemmas”; rows include אֱלֹהִים, יְהוָה | Same: **no Strong’s/open-lexeme citation.** Gap. |
| **Japanese stack** | About string names licenses (above). SOURCES.md only says “Already credited.” | **MUST copy license + project names into SOURCES.** Still **gap:** Kuromoji/JMdict **versions / snapshot dates**. |
| **About / Credits UI** | JA/EL/HE tables not in `pref_about_credits` yet | MUST add a line per shipped Greek/Hebrew asset once named. Empty credits + shipped TSV = R10. |

**Ship rule:** 1.0-RELEASE does not wait for handbook completeness
(REQUIREMENTS already allows pedagogical tables). It **does** wait for
every shipped map/TSV to have a SOURCES row that is either a real
citation or an explicit “project table, no external source.” Settings
labels that say “SBL academic” while the map is not the handbook are
R4 lock-in: rename the label when naming the gap, or users will cite
Πολγζλοξ as SBL.

### Issue #13 — Japan-only launcher is a brand lock (from R9)

The DUT screenshot `docs/screenshots/all-apps-japanglify.png` is the
1.0 identity as shipped: launcher label **Japanglify**, mark **ことば /
kotoba** on black. That is a Japanese-dictionary glyph. On GrapheneOS
Pixel 8 (and on Note9’s app drawer) it sits next to Keep, Kindle,
KeePass — one tap target, one story. Issue #13 exists because Polyglos
prefs already landed under that mark.

If 1.0-RELEASE keeps that icon (or only changes the About string to
Πολγζλοξ), the polyglot claim is a Settings footnote. PROCESS_TEXT
overflow, notification small-icon, and “unknown source” install
prompts will all still say Japan. Hebrew/Greek users will not look
for ことば; GrapheneOS reviewers will file it as a JA tool with extra
tables. Adaptive-icon + 24px PROCESS_TEXT affordance then ossify the
wrong silhouette (R1 keeps `applicationId`, so this icon is what
upgrades keep). A later “real” triple-script mark is a store-listing
event, not a drawable swap — people have already learned which blob
to tap. Issue #13’s Settings cruft pass (Polyglos first-class, Try-it
and Copy assist not the hero) is the in-app half of the same lock:
a Japan-only launcher plus a clipboard-assist-first home screen
teaches R2 even if the ingress contract above is written down.

### Sibling worktrees (skim; do not merge)

Base for all four: conductor `d35b8c8`. This tree: `c757301` notes.
Siblings as of this pass:

| Tree | Unique vs `d35b8c8` | vs these risks |
|---|---|---|
| **linguist** `…/worktrees/linguist` `agent/polyglos-linguist-20260921` | **No unique commit.** Uncommitted: inbox reply, SOURCES behavior facts, untracked `MixedScriptPassthroughTest.kt`. | **Aligns R8:** engine-gated passthrough; `forceAnalyzerId` cannot romanize a mixed word; Latin is not a mix partner. **Aligns R4 as a warning:** Settings “SBL” is not handbook SBL (`oy` vs `ou`). **Soft tension with R5:** inbox treats Ἰησοῦς / יְהוָה TSV rows as intentional lemmas, not issue #7. Compatible if emoji stays off those analyzers; still a gloss-ethics fact for R5, not a names-dictionary. Does **not** close the SOURCES citation gaps above. |
| **voice-ingress** `…/worktrees/voice-ingress` `agent/polyglos-voice-20260921` | **No unique commit.** Uncommitted docs retcon + untracked `domain/…/polyglos/voice/` protocol types/tests (`IngressSource` has no `CLIPBOARD`). | **Aligns R2/R3 contract:** Share + PROCESS_TEXT + live call; no clipboard ingress; no manifest VIS; GrapheneOS needs app `RecognitionService`. **Conflicts:** `VOICE_AND_PIPELINE` §6.1 still **prefers platform `SpeechRecognizer` first** (fails stock GrapheneOS) and mentions a **Play Feature Module** for Whisper (Play is a non-goal; GrapheneOS will not have it). That fights R3/R13. Protocol types are the agreed seam, not a 1.0 assistant export — do not merge as “voice shipped.” |
| **refinement-sync** `…/worktrees/refinement-sync` `7b16c75` | **Committed:** REQUIREMENTS clipboard → output-only; §4.2 per-token; §12.3 closed; `AnalyzerRegistry` KDoc; sync-log round 2. | **Aligns R2, R8, this ingress contract.** Their REQUIREMENTS §8 already matches Share / PROCESS_TEXT / optional chip / no copy-hook. **Stale vs us:** sync-log still says design researcher is kickoff-only; SOURCES still planned. **Do not merge their REQUIREMENTS from here** (this worker does not own that file). Conductor should take `7b16c75` docs + this notes file as two patches. Their KDoc fix is not in *this* worktree (`AnalyzerRegistry` here still says “dominant wins”). |

**AGENTS.md / DEVELOPMENT.md** (all trees, including this one) still
rank the a11y chip first and copy observation as fallback. That is
product-hierarchy drift vs the ingress contract. Align when
clipboard-input code is deleted — not in this notes-only commit.

**Lock (this file only):** no `.kt`, no REQUIREMENTS edits, no other
inboxes, no merge of sibling worktrees.

