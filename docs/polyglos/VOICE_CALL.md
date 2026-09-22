# Πολγζλοξ — Live voice call

**Status:** Design addendum to [VOICE_AND_PIPELINE.md](./VOICE_AND_PIPELINE.md)  
**Allowed code (this change):** protocol types + JVM tests only. No `VoiceInteractionService` class, no manifest `BIND_VOICE_INTERACTION`, no mic UI, no ASR model.  
**ASCII slug:** `polyglos`

Normative language: RFC 2119.

Hosts later: `VoiceInteractionSession`, on-device `SpeechRecognizer` / app `RecognitionService`, GrapheneOS / AOSP assistant role, live-caption-*style* continuous recognition. Those hosts MUST speak this protocol. They MUST NOT invent a second expand path.

---

## 1. Shape: a call, not a mic button

Voice is a **session** with the same four beats as a phone call. A final is one committed utterance, not hang-up. The call MAY stay live across many finals (live-caption style).

| Beat | Protocol event | Later Android host | ASR |
|---|---|---|---|
| Start | `VoiceCallEvent.Started` | `VoiceInteractionSession.onShow` / in-app Start | `startListening` |
| Partials | `VoiceCallEvent.Partial` | session UI text only | `onPartialResults` |
| Final | `VoiceCallEvent.Final` | commit utterance | `onResults` / segmented end |
| Hang up | `VoiceCallEvent.HungUp` | `onHide` / `finish` | `stopListening` / `cancel` |

```
IDLE ──Started──► LIVE ──HungUp──► HUNG_UP
                    │
                    ├── Partial (preview only; MUST NOT expand)
                    ├── Final   → IngressEvent(source=VOICE) → expand(text) → present
                    └── Failed  → FAILED
FAILED / HUNG_UP ──Started──► LIVE   (new call)
```

### 1.1 What MUST / MUST NOT happen

- **MUST** feed **final** (or user-confirmed) transcripts into the same `PolyglosEngine.expand(text, settings)` path as Share and `PROCESS_TEXT`.
- **MUST NOT** add `PolyglosEngine.expand(IngressEvent, …)`. Linguistics ignore ingress source. Hosts unwrap `event.text` (and MAY log `event.source`).
- **MUST NOT** call `expand` or any presenter on partials.
- **MUST NOT** write partials to the clipboard or fire result notifications.
- **MUST NOT** read the clipboard as voice input (clipboard **input** is being removed). Clipboard **output** of a finished expand remains allowed — same as read-only `PROCESS_TEXT`.
- **MUST NOT** require TTS, cloud ASR, or Accessibility as the speech path.
- Accessibility MAY only be a fallback for *showing* the user’s own call UI, never for capturing other apps’ text as “voice”.
- Empty `Final.text` MUST NOT produce an `IngressEvent`.

### 1.2 Ingresses (authoritative)

| Ingress | How text arrives | Today |
|---|---|---|
| Share | `ACTION_SEND` `text/plain` | `ShareTargetActivity` |
| `PROCESS_TEXT` | selection toolbar | `ProcessTextActivity` |
| Voice | `VoiceCallEvent.Final` → `IngressEvent` | protocol types only |

Clipboard assist / copy-hook is **not** an ingress going forward. `IngressSource` MUST NOT contain `CLIPBOARD`.

---

## 2. Protocol types (domain, pure JVM)

Package: `com.japanglify.app.domain.polyglos.voice`. No `android.*` imports.

### 2.1 Ingress

```
enum class IngressSource { PROCESS_TEXT, SHARE, VOICE }

data class IngressEvent(
  source: IngressSource,
  text: String,
  localeHint: String?   // BCP-47 from ASR or null
)
```

`VoiceCall.toIngressEvent(Final)` is the only voice → ingress converter. Share / `PROCESS_TEXT` MAY construct `IngressEvent` when those activities are next touched; this change MUST NOT rewrite them.

### 2.2 Call events and phase

```
sealed class VoiceCallEvent {
  data object Started
  data class Partial(text: String)
  data class Final(text: String, localeHint: String? = null)
  data object HungUp
  data class Failed(reason: String)
}

enum class VoiceCallPhase { IDLE, LIVE, HUNG_UP, FAILED }
```

### 2.3 State machine

`VoiceCallStateMachine.apply(event)` is the single transition function later hosts call.

| From | Event | To | Side effect |
|---|---|---|---|
| `IDLE`, `HUNG_UP`, `FAILED` | `Started` | `LIVE` | none (host starts ASR) |
| `LIVE` | `Partial` | `LIVE` | `previewText` only |
| `LIVE` | `Final` (non-blank) | `LIVE` | `ingress = IngressEvent(VOICE, text, localeHint)` |
| `LIVE` | `Final` (blank) | `LIVE` | no ingress |
| `LIVE` | `HungUp` | `HUNG_UP` | host stops ASR; no present |
| `LIVE` | `Failed` | `FAILED` | `failureReason`; no ingress |
| `IDLE` / `FAILED` | `HungUp` | `HUNG_UP` | idempotent hang-up |
| any other | * | unchanged | `accepted = false` |

`Started` while already `LIVE` is rejected (do not nest calls). Partials/finals outside `LIVE` are rejected.

### 2.4 Host seams (stubs, still JVM)

Later Android classes implement these. They are interfaces only:

- `VoiceCallController` — start / hang up / fail / ASR callbacks into the machine.
- `VoiceCallHost` — listening / partial / idle / failed UI.
- `VoiceExpandSink` — receives `IngressEvent` for expand+present.
- `AsrBackend` / `AsrListener` — offline recognizer behind the controller.

No `Service` subclass in this change.

---

## 3. Smallest OS-hostable call

The in-app Try-it mic (when it lands) is a **host** of this protocol, not a second product. `VoiceInteractionSession` is the later-OS host of the **same** call.

### 3.1 `VoiceInteractionService` / `VoiceInteractionSession` (later)

When implementation is unblocked (P4):

1. User invokes the assistant (long-press home / nav corner / `RoleManager` assistant / in-app Start).
2. `VoiceInteractionSession.onShow` → `Started` (small live UI: listening, partial text, hang up).
3. Partials update that UI.
4. Each accepted `Final` → `IngressEvent(VOICE)` → `engine.expand(text, settings)` → existing presenter (notification / clipboard **output** / overlay).
5. Hang up → `onHide` / `finish`; cancel recognition; no extra present.

Assist `AssistStructure` / screenshot (`onHandleAssist`, `onHandleScreenshot`) is **out of the first call**. Those are later-OS “what is on screen” assists. The 1.0 voice job is the user’s **own speech**, not scraping the host app.

Do **not** register the service in the manifest until P4: exporting `BIND_VOICE_INTERACTION` makes Πολγζλοξ an assistant candidate on GrapheneOS and fights Bixby on Note9. This document MUST NOT dump a full `VoiceInteractionService` registration (intent filters, `voice_interaction.xml`, recognition-service meta-data).

### 3.2 On-device `SpeechRecognizer` / `RecognitionService`

Preferred backends (first that is **offline** for the active locale):

1. Platform `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE` (API 23+). On API 31+ prefer `createOnDeviceSpeechRecognizer` / `isOnDeviceRecognitionAvailable` when the OEM actually ships a recognizer.
2. **App-owned** `RecognitionService` + bundled or user-downloaded model (GrapheneOS default path — see §4).
3. Explicit user opt-in “Allow online speech recognition” (default **OFF**). MUST NOT be silent.

`VoicePlatformProfile.forSdk(sdk)` gates APIs that do not exist on Note9. Capability **API** ≠ capability **present**: GrapheneOS Pixel 8 has the API 31+ methods and still has no Google `RecognitionService`.

### 3.3 Live-caption style (continuous)

This means **our** call stays in `LIVE` and keeps emitting partials + finals, like Live Caption’s UX. It does **not** mean hooking the system Live Caption pipeline (not a public app API).

| SDK | How to keep the call live |
|---|---|
| 33+ | `RecognizerIntent.EXTRA_SEGMENTED_SESSION` (and related extras) when the backend supports it |
| 29 (Note9) | After each `onResults`, if still `LIVE`, `startListening` again. Treat `onEndOfSpeech` as a pause, not hang up |

Restart-on-final is the Note9-compatible continuous call. Segmented session is an optimization on later OS. `VoicePlatformProfile.restartListeningAfterFinal` is `true` when `sdk < 33`.

### 3.4 Assistant role (later OS)

- API 29+: `RoleManager.ROLE_ASSISTANT` exists (Note9 included).
- GrapheneOS / AOSP: Settings → default digital assistant can be a third-party `VoiceInteractionService`.
- Samsung Note9: Bixby usually holds the slot; the in-app host MUST work **without** winning the assistant role.
- Do not prompt for the assistant role on first expand. Offer it from Settings when the service is actually registered.

---

## 4. Device constraints (Note9 / 9185W / GrapheneOS Pixel 8)

`minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`.

UAT pool:

| Device | OS / SDK | How attached | Voice implication |
|---|---|---|---|
| Galaxy Note9 (`SM-N960U`) | **API 29** (Android 10) | USB | Classic `SpeechRecognizer` only; restart-on-final; no mic FGS type |
| 9185W | **Android 15 / API 35** | wireless debug (drops) | APIs exist; OEM `RecognitionService` unknown; prefer USB for first voice UAT |
| GrapheneOS Pixel 8 | **AOSP 15-class / API 35** | USB (when present) | APIs exist; stock profile has **no** Google recognizer |

### 4.1 Galaxy Note9 — API 29 — what it **cannot** do

The Note9 is still a required UAT device. Voice on Note9 is the **in-app or `VoiceInteractionSession` call** using classic `SpeechRecognizer`, not the Android 12+ on-device recognizer APIs.

| Missing (API) | Consequence |
|---|---|
| `SpeechRecognizer.createOnDeviceSpeechRecognizer` (31) | MUST use `createSpeechRecognizer` + `EXTRA_PREFER_OFFLINE` |
| `isOnDeviceRecognitionAvailable` (31) | Cannot probe OEM on-device STT; treat unknown as “try offline extra, then fail clearly” |
| `checkRecognitionSupport` / `RecognitionSupport` (33) | No per-locale support query |
| `EXTRA_SEGMENTED_SESSION` (33) | Continuous call = restart listening after each final |
| `FOREGROUND_SERVICE_TYPE_MICROPHONE` (31) | If a recording FGS is ever needed, Note9 cannot declare `microphone`; prefer session-scoped recognition **without** an FGS |
| Live Caption internals | Even where the OEM shipped Live Caption (Android 10), it is not an app API |

Also Note9-specific (not just API level):

- **Bixby** typically owns the assistant / device-assistance slot. Πολγζλοξ MUST expand voice without being the default assistant.
- Samsung STT may be network-backed. Offline-first on Note9 is **best-effort**: if `EXTRA_PREFER_OFFLINE` fails, explain and stop; do not silently use the network.
- OEM `RecognitionService` is not something we can ship into Samsung’s slot. Bundled-model `RecognitionService` is the only reliable offline path if Samsung/Google STT is absent or online-only.

`RoleManager.ROLE_ASSISTANT` **does** exist on API 29. Winning it on Samsung is optional and MUST NOT be required for the call.

`VoicePlatformProfile.forSdk(29)`: no on-device recognizer API, no recognition-support API, no segmented session, no microphone FGS type, `restartListeningAfterFinal = true`, `roleAssistantApi = true`.

### 4.2 9185W — Android 15 wireless DUT

9185W is the wireless-debug pool device (`docs/agent-handoff.md`). It runs **Android 15** (API 35), so `VoicePlatformProfile.forSdk(35)` matches GrapheneOS Pixel 8 for **API gates**.

Until a UAT note records whether a `RecognitionService` is installed:

- MUST NOT assume a working on-device recognizer just because the API 31+ methods exist.
- MUST still feature-detect at runtime (`isOnDeviceRecognitionAvailable` / `createOnDeviceSpeechRecognizer` failure → `Failed`).
- Wireless debugging drops; voice UAT SHOULD prefer USB for the first call milestone.
- OEM / carrier image (not GrapheneOS). Online Samsung/Google STT MAY be present; the default path is still offline-first with no silent network fallback.

### 4.3 GrapheneOS Pixel 8 (AOSP 15-class)

APIs **exist** (`createOnDeviceSpeechRecognizer`, segmented session, `ROLE_ASSISTANT`, microphone FGS type). The **implementation** behind them often does not:

- No Google app / Speech Services by Google on a stock GrapheneOS profile.
- AOSP `SpeechRecognizer` is a client. With no `RecognitionService`, `createSpeechRecognizer` / `createOnDeviceSpeechRecognizer` fail. That is the default.
- Sandboxed Play is optional and MUST NOT be required for voice.
- GrapheneOS extra **Network** permission: even an “online ASR” opt-in can be denied at the OS toggle. Offline-first is the only path that matches the OS.
- User can grant **Digital assistant** to a third-party `VoiceInteractionService`. This is the intended later-OS host for the call.
- Assist screenshots / structure: do not require; privacy-hardened profiles may deny them.
- `RECORD_AUDIO` is still a runtime permission; request only when starting the call.

**GrapheneOS voice MVP** is therefore: our call protocol + **app `RecognitionService` + on-device model** (or a user-installed third-party recognizer). Platform `SpeechRecognizer` alone is not a plan.

### 4.4 Android 15 (API 35) vs this app

Compile/target 35 allows later-OS assist (assistant role, segmented recognition, on-device recognizer APIs). None of those APIs may be called without an SDK guard: Note9 is API 29. Live Caption remains a **system** feature; we copy its continuous-partial UX, not its process.

`VoicePlatformProfile.forSdk(35)`: on-device recognizer API, recognition-support API, segmented session, microphone FGS type, `restartListeningAfterFinal = false`, `roleAssistantApi = true`. Hosts MUST still handle segmented extras being ignored by the backend (fall back to restart-on-final).

---

## 5. Present after a final

Same presenters as Share / read-only `PROCESS_TEXT`:

- Clipboard **output** of the expanded string is OK.
- Result notification / overlay is OK.
- Partials: in-call text view only.

`source=VOICE` is telemetry and UX copy (“Expand” still, not “voice convert”). Linguistics ignore it.

Gateway (when P1 lands): `VoiceExpandSink` → `IngressProcessor` / `ExpandSession` → `PolyglosEngine.expand(text, settings)`. Until then, a host MAY call `expand` directly with the final text, same as `ProcessTextActivity` does today.

---

## 6. Phasing (voice-specific)

| Phase | Deliverable |
|---|---|
| **Now** | This doc + protocol types + domain tests; `VOICE_AND_PIPELINE.md` retconned to live-call ingress |
| **P3a** | In-app host implementing `VoiceCallController` (Try-it / a session activity) — same protocol as the later session |
| **P3b** | Offline ASR backend behind `AsrBackend`; GrapheneOS-usable `RecognitionService` |
| **P4** | Exported `VoiceInteractionService` + session UI; optional `ROLE_ASSISTANT` |
| **Not Note9** | `createOnDeviceSpeechRecognizer`, segmented extras, microphone FGS — gated, never required |

Full feature code stays deferred until an explicit ticket unblocks P3a. The protocol types are the exception.

---

## 7. Document history

| Date | Change |
|---|---|
| 2026-09-21 | Live-call addendum: VoiceInteractionSession mapping, offline ASR, GrapheneOS / Note9 / 9185W gates |
| 2026-09-21 | Finish protocol: state machine, ingress (no clipboard), 9185W = Android 15 vs Note9 API 29 vs GrapheneOS Pixel 8; types in `domain` |
