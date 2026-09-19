# Πολγζλοξ (polyglos) — Voice input, unified pipeline & cold-start

**Status:** Design / requirements (normative for planning)  
**Implementation:** **Deferred** until higher-priority 1.0 features land (analyzers, settings, device UAT). This document MUST be kept current; code MUST NOT start until an explicit implementation ticket/milestone unblocks it.  
**ASCII slug:** `polyglos`  
**Related:** [REQUIREMENTS.md](./REQUIREMENTS.md), PR #11 core engine

Normative language: RFC 2119 (`MUST`, `MUST NOT`, `SHOULD`, `MAY`).

---

## 1. Purpose

Add an **offline-first voice capture** path that turns a live speech stream into text, then feeds the **same** polyglos expand / re-render pipeline used by PROCESS_TEXT, share, clipboard assist, and selection overlay — so voice is not a parallel product, just another ingress.

Also: tighten the existing ingress → expand → present flow so it feels like one natural loop, and specify how to eliminate **cold-start hangs** that block normal use today.

---

## 2. Goals

1. **Voice stream in, triple-script (and polyglot analysis) out** — no TTS / spoken reply required.
2. **Offline critical** — after install (and any one-time on-device model download the user opted into), airplane mode MUST still allow capture→expand for enabled languages.
3. **Word-for-word / near-verbatim ASR** — prefer faithful transcripts over “smart” paraphrasing; polyglos owns linguistic enrichment.
4. **Mixed-but-mostly-one language** — natural speech is usually one primary language with occasional borrowings; routing MUST favor that reality while allowing run-level switches.
5. **User language pack control** — enable / disable / priority-order languages (and ASR locales) to raise accuracy and cut model work.
6. **Single re-render spine** — voice results MUST enter the same `PolyglosEngine` / `JapanglifyEngine.expand` + present path as text selections.
7. **Naturalize ingress** — reduce duplicate “process clipboard / process selection / share / PROCESS_TEXT” UX forks over time.
8. **Cold-start non-blocking** — first expand after process start MUST NOT freeze the UI or miss the user’s gesture; warmup MUST be explicit and cancelable.

---

## 3. Non-goals (this design phase / first voice milestone)

- Cloud ASR or cloud translation as a required path.
- Spoken output (TTS), conversation agents, or continuous “always listening” wake-word IME replacement (MAY be explored later).
- Perfect code-switching ASR equal to bilingual humans.
- Implementing voice or cold-start fixes in the same change set as this document (design only).

---

## 4. Definitions

| Term | Meaning |
|---|---|
| **Ingress** | How text arrives: PROCESS_TEXT, share, clipboard assist, selection overlay, **voice**, Try-it, etc. |
| **Transcript** | Raw ASR text (and optional per-token timing / confidence). |
| **Expand** | `engine.expand` / interlinear builders — analyzers + renderer. |
| **Present** | Clipboard write, notification, overlay, image render, share sheet result — anything that shows expand output. |
| **Language pack** | User-visible enable + priority for a polyglos analyzer id (`ja`, `grc-ancient`, `el-modern`, `hbo`, `he-modern`, …) and its linked ASR locale(s). |
| **ASR backend** | On-device speech-to-text implementation behind a narrow interface. |
| **Cold start** | First use after process create: Kuromoji/dict load, engine rebuild, emoji/gloss DB open, image pipeline, etc. |

---

## 5. End-to-end pipeline (normative)

```
[Mic / IME voice stream]
        │
        ▼
 VoiceCaptureController  (app)     — permission, session, partials
        │  Audio frames / system recognizer callbacks
        ▼
 AsrBackend (app, offline)         — locale hint from LanguagePackPolicy
        │  Transcript (+ optional word timings)
        ▼
 IngressNormalizer                 — same shape as clipboard/selection text
        │  IngressEvent { source=VOICE, text, localeHint?, packsSnapshot }
        ▼
 PolyglosEngine / JapanglifyEngine.expand   (domain)
        │  ScriptDetector ∩ enabled packs ∩ priority
        ▼
 Presenter (existing)              — clipboard / notif / overlay / image
```

### 5.1 Feed into re-render

- Voice completion MUST produce an `IngressEvent` (name MAY differ) that is **indistinguishable in expand/present** from a successful selection/clipboard process, aside from telemetry `source=VOICE`.
- Partial ASR results MAY update a lightweight preview UI; they MUST NOT spam clipboard or notifications. Only **final** (or user-confirmed) transcripts MUST trigger the full present path by default.
- Settings that affect expand (force analyzer, Greek Erasmian/SBL, gloss/emoji, output format) MUST apply to voice the same way they apply to PROCESS_TEXT.

### 5.2 Cleanup / naturalization of existing paths (design debt to burn down)

Today multiple call sites invoke `engine.expand` directly (`ClipboardProcessor`, accessibility overlay, image cache, etc.). Requirements for cleanup (implement when touching those areas, not blocked on voice code):

1. **One expand gateway** — app-layer SHOULD route all ingress through a single facade (e.g. `ExpandSession` / `IngressProcessor`) that: loads settings once, applies size limits, dedupes, calls engine, then presents.
2. **Japanese-only guards** — e.g. `containsJapanese` short-circuits MUST become **pack-aware** (enabled languages / script detection), not hard-coded JA.
3. **User-facing verbs** — Prefer one primary action (“Expand” / brand name) across PROCESS_TEXT label, notifications, and voice end-state; avoid “process clipboard” vs “japanglify selection” vs “voice convert” as three mental models.
4. **Try-it card** — SHOULD accept pasted **or** last voice transcript for parity testing without leaving Settings.

---

## 6. Offline ASR requirements

### 6.1 Backend interface

```
interface AsrBackend {
  fun isAvailableOffline(locale: AsrLocale): Boolean
  fun supportedLocales(): Set<AsrLocale>
  fun start(session: AsrSessionConfig, listener: AsrListener)
  fun stop()
  fun cancel()
}
```

- App MUST NOT call cloud STT APIs on the default path.
- Preferred order of backends (implementation picks first that satisfies offline for the active locale):
  1. **Platform on-device `SpeechRecognizer`** with `EXTRA_PREFER_OFFLINE` when the OEM ships the locale.
  2. **Bundled / user-downloaded on-device model** (e.g. Whisper.cpp / similar) via a Play Feature Module or explicit download screen — still offline at recognition time.
- If no offline backend is available for the user’s **highest-priority enabled** ASR locale, the UI MUST explain that and offer language-pack / download options — MUST NOT silently fall back to network ASR unless the user opts into an explicit “Allow online speech recognition” setting (default OFF).

### 6.2 Streaming UX

- Behavior SHOULD match familiar voice-input patterns: hold-to-talk and/or tap-to-toggle, partial text, cancel, retry.
- Capture MAY live as: in-app mic on Try-it / result screen; optional later: `VoiceInteractionSession` / IME-adjacent entry. First milestone SHOULD be **in-app** to avoid IME store complexity.
- `RECORD_AUDIO` only when starting capture; rationale string MUST mention offline on-device recognition.

### 6.3 Transcript quality bar

- Target: usable word-level transcript for expand, not broadcast caption perfection.
- Preserve punctuation if the backend provides it; do not invent glosses in the ASR layer.

---

## 7. Multilingual policy (mixed-but-mostly-one)

### 7.1 Language packs (user control)

Settings MUST eventually expose a **Language packs** list (can ship after 1.0 voice impl starts):

| Control | Behavior |
|---|---|
| **Enable** | Disabled packs MUST NOT be chosen by ScriptDetector auto-route and MUST NOT be offered as ASR locales. |
| **Priority order** | Stable user-ordered list. When scores tie or speech locale is ambiguous, higher priority wins. |
| **Remove / hide** | Same as disable for routing; UI MAY use “Remove from active set”. |
| **ASR locale mapping** | Each pack maps to one or more `AsrLocale` ids (e.g. `ja` → `ja-JP`; `el-modern` → `el-GR`; ancient Greek MAY map to modern Greek ASR + polyglos ancient analyzer — MUST be documented as a limitation). |

Defaults for a fresh install SHOULD enable **Japanese** first; Greek/Hebrew packs MAY start disabled until the user opts in (reduces cold work and ASR confusion). Final default set is an open decision (§12).

### 7.2 Speech routing algorithm

On each final transcript:

1. Intersect **enabled packs** with ScriptDetector profile on the transcript text.
2. If `forceAnalyzerId` is set (existing setting), honor it.
3. Else if detector finds a dominant script among enabled packs → that analyzer.
4. Else use **speech locale hint** from AsrBackend mapped through pack table.
5. Else fall back to highest-priority enabled pack.
6. Mixed script runs: same policy as REQUIREMENTS §7 (dominant + passthrough for 1.0; per-run split SHOULD).

### 7.3 Accuracy levers (why priority/remove matters)

- Fewer active ASR locales → better on-device model selection / less wrong-language decoding.
- Fewer enabled analyzers → fewer false “Hebrew punctuation” style mis-routes on symbols.
- Priority breaks ties without a settings rabbit hole mid-utterance.

---

## 8. Cold-start & hangs (normative design)

Observed risk today: `JapanglifyApp.onCreate` constructs `KuromojiReadingProvider` and `rebuildEngine()` on the main path; first `expand` from accessibility/clipboard can hitch when dictionaries/emoji DBs initialize.

### 8.1 Requirements

1. **UI thread MUST NOT** perform Kuromoji constructor, large asset unpack, or glossary DB open.
2. **Deferred warmup** — After `Application.onCreate`, schedule engine/dict warmup on a background executor; expose `EngineState { COLD, WARMING, READY, FAILED }`.
3. **First gesture** — If user triggers expand while `WARMING`, UI MUST show non-blocking progress (notif or overlay) and complete when READY; MUST NOT ANR. Prefer queueing one expand rather than parallel warmups.
4. **Timeouts** — Warmup SHOULD bound wait (e.g. notify failure after N seconds) with retry.
5. **Voice-specific** — ASR model load MUST also be background; mic UI can open immediately with “Preparing recognition…” if needed.
6. **Idle reclaim** — MAY unload heavy models after idle; next use re-enters WARMING (same UX).

### 8.2 Suggested ownership

| Component | Responsibility |
|---|---|
| `JapanglifyApp` | Own `EngineState`, kick warmup, `rebuildEngine` off main thread |
| `IngressProcessor` | Await READY with UX, single-flight expands |
| Voice milestone | Warm ASR backend independently; don’t block on Kuromoji until transcript final |

These cold-start requirements MAY be implemented **before** voice (they help all ingress). Voice implementation remains deferred.

---

## 9. Permissions, privacy, security

- Mic: runtime `RECORD_AUDIO`; no audio upload on default path.
- Audio buffers MUST NOT be written to network storage; optional user-initiated bug report MAY attach transcript text only (not audio) unless user explicitly attaches audio.
- On-device model downloads MUST use HTTPS and verify size/hash when applicable.

---

## 10. Testing requirements (when implementation starts)

- Unit: LanguagePackPolicy priority / disable / forceAnalyzer interaction with ScriptDetector fixtures.
- Unit: IngressEvent from VOICE vs CLIPBOARD hits same expand gateway mock.
- Instrumented: offline flag true → no network permission used during a recognition session (or mock backend).
- Manual UAT: airplane mode, JA voice → expand; Greek pack enabled + locale; disabled pack not used; cold start expand shows warmup not freeze.
- Regression: PROCESS_TEXT / clipboard still work with gateway refactor.

---

## 11. Phasing

| Phase | Deliverable | Notes |
|---|---|---|
| **P0 — this doc** | Requirements frozen for planning | No feature code |
| **P0b — cold-start** | EngineState + off-main warmup | Allowed early; unblocks all ingress |
| **P1 — gateway cleanup** | Single expand/present facade; pack-aware guards | Natural UX; prep for voice |
| **P2 — language packs UI** | Enable/priority settings | Builds on force-analyzer prefs |
| **P3 — voice MVP** | In-app capture + offline ASR + IngressEvent | After P0b–P2 as needed |
| **P4 — polish** | Partials preview, downloads module, optional IME entry | |

---

## 12. Open decisions

1. Default enabled packs on fresh install (JA-only vs JA+el+he).
2. Ancient Greek / Biblical Hebrew ASR locale mapping (modern locale + analyzer force vs dedicated models).
3. Bundled vs downloadable Whisper-class models (APK size vs first-run download).
4. Whether hold-to-talk lives on notification, Try-it, or both for MVP.
5. Exact `EngineState` timeout values and whether clipboard assist auto-retries after READY.

---

## 13. Document history

| Date | Change |
|---|---|
| 2026-09-19 | Initial design: voice ingress, unified re-render spine, language packs, cold-start; implementation deferred |