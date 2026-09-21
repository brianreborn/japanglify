# Sync log

## 2026-09-21 — start

Workers: linguist, voice, design researcher, this sync task.
Constraint: refinements only; no significant new features.

## 2026-09-21 — ownership lock

See `OWNERSHIP.md`. Sync writes **this log only** until the conductor
commits mixed-input. Do not edit REQUIREMENTS or Kotlin to “align.”

## 2026-09-21 — alignment (log-only; OWNERSHIP still holds)

Workers read. No REQUIREMENTS / Kotlin / other-inbox edits this round
(`OWNERSHIP.md`: refinement-sync → `sync-log.md` only). Durable copy
into REQUIREMENTS waits until conductor releases §7 / clipboard hold.

### Authoritative facts (still true)

1. Mixed **input** (separate words) is accepted; each monolingual word
   routes to its analyzer. Mixed-script **words** (ja/el/he letters in
   one whitespace token) pass through base-only — same as unknown text.
   Engine already does this in `PolyglosEngine.annotate` (tokenize →
   `profile.mixed` → passthrough; else `registry.select`). Tests:
   `PolyglosSettingsRoutingTest.mixedInputSeparateWordsEachLanguageAnalyzed`,
   `mixedScriptWordIsPassthrough`. Linguist need not duplicate that file;
   optional extra coverage (force-analyzer still passthrough; he+el
   mash; unpointed `he-modern`) is idle work, not a new test file unless
   a romanizer still force-reads a mixed token when called directly.
2. Clipboard **input** is being removed; clipboard **output** (text /
   optional PNG after expand) stays.
3. Ingress for 1.0+: Share + `PROCESS_TEXT` + later voice-as-live-call.
   Accessibility selection chip may remain as a selection UX; it must
   not become clipboard surveillance.
4. Languages that MUST work: `ja`, `grc-ancient`, `el-modern`, `hbo`,
   `he-modern`. Auto-detect currently prefers ancient Greek/Hebrew
   (registry order + `supports` on dominant script); modern ids work
   via `forceAnalyzerId` (`PolyglosEngineTest.forceOverride` for
   `el-modern`; `he-modern` force/unpointed is thinner).

### Drift to close when locks lift (do not implement here)

| Location | Drift |
|---|---|
| REQUIREMENTS §1.5, §8, §8.1, §11, §12a | Still list clipboard-assist / copy-hook as preserved **ingress**. Should be output-only; UAT = Share + PROCESS_TEXT (+ Greek/Hebrew samples), not clipboard poll. |
| REQUIREMENTS §4.2 pipeline | Document-level single analyzer. Code is per-token. |
| REQUIREMENTS §12.3 | Still “open: dominant + passthrough”. §7 already closed mixed-input vs mixed-word. Close §12.3. |
| REQUIREMENTS §12 (new default) | Greek auto → `grc-ancient`; Hebrew auto → `hbo`; modern via force / later packs. |
| `AnalyzerRegistry` KDoc | Stale “dominant wins (passthrough for other runs)”. Engine owns mixed-word passthrough. |
| VOICE_AND_PIPELINE §1, §4, §5, §7.2.6, §10, §12.5 | Clipboard assist as ingress; mixed line still “dominant + passthrough”; P3 “in-app mic” vs agreed live **call**; CLIPBOARD IngressEvent tests. Voice worker owns this file. |
| `ClipboardProcessor.containsJapanese` | App still JA-only-gates clipboard/a11y. Belongs to clipboard-input removal, not a new analyzer. |
| AGENTS.md / DEVELOPMENT.md product hierarchy | Still ranks copy-observation as fallback ingress. Align after clipboard-input removal lands. |
| SOURCES.md | Still “planned / candidate”; no new citations from linguist yet. |

### Worker state

- **Linguist:** Kickoff on mixed-input vs mixed-word. Inbox lock: reply
  in inbox; do not edit engine/REQUIREMENTS. Coverage already in
  `PolyglosSettingsRoutingTest`. Flag if `GreekRomanizer` /
  `HebrewRomanizer` / `JapaneseAnalyzer` would still romanize a mixed
  token if called *directly* (engine currently never hands them one).
- **Voice:** Kickoff asks for smallest `VoiceInteractionSession` “call”
  that feeds `PolyglosEngine`. Design in `VOICE_AND_PIPELINE.md` /
  optional `VOICE_CALL.md` only. **No `VOICE_CALL.md` yet.**
- **Design researcher:** Kickoff only (mixed-input, drop clipboard
  input, live-call voice, GrapheneOS, logo/settings, package id).
  Notes file only.

### DEFER (do not implement)

- **Voice implementation** (VoiceInteractionService/Session, ASR
  backends, RECORD_AUDIO, in-app mic, IME). Design/docs only until an
  explicit milestone unblocks code. Live-call *shape* is the agreed
  seam; P3 “in-app first” in VOICE_AND_PIPELINE is outdated vs user
  “call” — voice worker should retcon the planning doc, not land APIs.
- **TTS / spoken reply.**
- **Cloud ASR as required path.**
- **Play Store / AAB publishing.**
- **Clipboard-input revival** (keep removal).
- **New analyzers / language packs UI / Whisper models.**
- **Full package rename** (`com.polyglos…`) if it blocks 1.0.
- **Unspaced mixed-run split** (REQUIREMENTS §7 SHOULD later).

### Not started (correct)

Gradle `:domain:test` not launched from this worker (OWNERSHIP: one
daemon may already be running).

Next sync round after conductor commit: copy facts 1–4 into
REQUIREMENTS (clipboard ingress language, close §12.3, per-token
pipeline) and ask voice to patch VOICE_AND_PIPELINE ingress + mixed
policy lines.
