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

## 2026-09-21 — round 2 (isolated worktree; mixed-input committed)

Base: `d35b8c8` (`feat(polyglos): mixed-script word passthrough and specialist briefs`).
Worktree: `QODESH/swarm-bench/worktrees/refinement-sync` on `agent/polyglos-refinement-sync`.
Conductor lock on §7 / engine mixed-input is **lifted** (code is on HEAD). This round
copies durable facts; no new features, no voice APIs, no TTS, no new analyzers.

### Aligned in this worktree

| Item | Where |
|---|---|
| Mixed **input** vs mixed-script **word** | §7 already correct at `d35b8c8`. **§12.3 closed** (was “dominant + passthrough”). |
| Per-token pipeline | **§4.2** rewritten; §3 Engine row now tokenize → per-token detect → analyze. |
| Clipboard is **output only** | §1.5, §8, §8.1 UAT (Share + PROCESS_TEXT, not copy-hook poll), §11.3, §12a. |
| `AnalyzerRegistry` KDoc | No longer “dominant wins (passthrough for other runs)”. Engine owns mixed-word passthrough before `select`. |

### Still not this worker

- **Voice:** `VOICE_AND_PIPELINE.md` still lists clipboard assist as ingress and mixed line as dominant+passthrough. `VOICE_CALL.md` already matches (Share + PROCESS_TEXT + voice; clipboard not ingress). Voice worker should retcon `VOICE_AND_PIPELINE.md` §§1, 4, 5, 7.2.6, 10, 12.5 — design only.
- **Clipboard-input removal in app code** (`ClipboardProcessor.containsJapanese`, FGS poll). Spec now forbids it; implementation is a coder task, not a new analyzer.
- **AGENTS.md / DEVELOPMENT.md** still rank copy-observation as fallback ingress. Align when clipboard-input code is deleted.
- **SOURCES.md** still planned/candidate.
- Linguist coverage remains in `PolyglosSettingsRoutingTest`; no extra test file from sync.
- Gradle `:domain:test` not launched here (KDoc + docs only).

### DEFER (unchanged)

Voice implementation, TTS, Play/AAB, new analyzers, unspaced mixed-run split (§7 SHOULD later), full `com.polyglos…` package rename if it blocks 1.0.

## 2026-09-21 — round 3 (sibling skim + playbook hierarchy)

Worktree still `…/worktrees/refinement-sync` on `agent/polyglos-refinement-sync`.
Shared `agent/polyglos-1.0-core` remains `d35b8c8` (ahead 1 of origin). This
round does **not** merge into core.

### Sibling worktrees (`QODESH/swarm-bench/worktrees/`)

| Tree / branch | HEAD | Dirty? | What they have | Mergeable? | Conflict vs this sync |
|---|---|---|---|---|---|
| **design-researcher** `agent/polyglos-design-20260921` | `c757301` | clean | `docs/agents/design-research-notes.md` only (R1–R14 reverse-hard risks). Correct facts: drop clipboard input, chip as selection, keep applicationId, auto-ancient, mixed-word passthrough. Notes still *say* REQUIREMENTS §1.5/§8/§12.3 are stale — that was true at `d35b8c8`; **7b16c75 already closed those**. | **Yes** (notes file). Treat the “REQUIREMENTS still stale” paragraph as a snapshot, not a reason to reopen §12.3. | None (different files). |
| **linguist** `agent/polyglos-linguist-20260921` | `d35b8c8` | **uncommitted** | Inbox reply (engine-gated mixed; romanizers still force-read if called directly); `SOURCES.md` 1.0 facts (Latin is not a mix partner; Erasmian/SBL letter-map limits; unpointed `he-modern`); new `MixedScriptPassthroughTest.kt`. | **Not ready** until they commit. Then **yes**: inbox + SOURCES + extra test. Idle coverage, not a new analyzer. Inbox still asks sync to close §12.3 — already done here. | None expected vs sync files. Do **not** let linguist re-edit REQUIREMENTS or `AnalyzerRegistry`. |
| **voice-ingress** `agent/polyglos-voice-20260921` | `d35b8c8` | **uncommitted** | `VOICE_AND_PIPELINE.md` retcon (clipboard not ingress; live call not in-app mic); `VOICE_CALL.md` expanded; `docs/polyglos/README.md` +1 line; protocol types under `domain/…/polyglos/voice/` (`IngressSource` PROCESS_TEXT/SHARE/VOICE only, state machine, platform profile) + tests. No `VoiceInteractionService` / ASR impl in the untracked tree. | **Not ready** until they commit. Then **mergeable as design + protocol types** after conductor confirms no app/manifest registration. | None vs sync files. Voice owns VOICE_*; we did not touch them. Protocol Kotlin is new paths. |
| **refinement-sync** (this) | `7b16c75`+ | this round | REQUIREMENTS, registry KDoc, now AGENTS/DEVELOPMENT/GLOSSARY hierarchy. | Merge **after** core stays at mixed-input. | — |

### Playbook nits this round (this worktree)

- `AGENTS.md` / `DEVELOPMENT.md`: hierarchy is Share + PROCESS_TEXT, later live-call, optional selection chip, clipboard **output** only.
- `docs/GLOSSARY.md`: PROCESS_TEXT / Copy assist definitions match.
- `ARCHITECTURE.md` / root `README.md` still describe **current app code** (copy-hook exists). Left as implementation description until a coder deletes clipboard-input; playbooks no longer rank it as product direction.

### Proposed merge order (conductor only — do not land here)

1. **Keep** `agent/polyglos-1.0-core` at **`d35b8c8`** as the integration base. Mixed-input is already there; do not re-apply engine/tokenizer patches.
2. **This sync branch** (`agent/polyglos-refinement-sync`, `7b16c75` + this commit): REQUIREMENTS, `AnalyzerRegistry` KDoc, AGENTS/DEVELOPMENT/GLOSSARY, sync-log. Lowest overlap; gives linguist/design a closed §12.3 to read.
3. **Design** `c757301` (`design-research-notes.md`). Fast, one file. Optional follow-up: one sentence that REQUIREMENTS drift was closed by sync — not required to merge.
4. **Linguist** after they **commit**: `linguist-inbox.md`, `SOURCES.md`, `MixedScriptPassthroughTest.kt`. Run `:domain:test` once on the merge tree (not a second daemon on `dev/` while another is running).
5. **Voice** after they **commit**: VOICE docs + `domain/…/polyglos/voice/` protocol. Last among specialists so pipeline language is checked against REQUIREMENTS §4.2 / §8. Reject if the commit grows a service, RECORD_AUDIO, or TTS.

If linguist and voice commit in parallel, they still do not share files; order 4 then 5 is review convenience, not a git constraint.

### DEFER (unchanged)

Voice **implementation** (session/service/ASR), TTS, Play/AAB, new analyzers, clipboard-input **code** deletion (coder), unspaced mixed-run split, full package rename.
