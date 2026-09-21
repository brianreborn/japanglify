# Πολγζλοξ — Live voice call

**Status:** Design addendum to [VOICE_AND_PIPELINE.md](./VOICE_AND_PIPELINE.md)  
**Allowed code:** protocol types only (`IngressEvent`, `VoiceCallEvent`, `VoiceCallStateMachine`, `VoicePlatformProfile`). No `VoiceInteractionService` registration, no mic UI, no ASR model.  
**ASCII slug:** `polyglos`

Normative language: RFC 2119.

---

## 1. Shape: a call, not a mic button

Voice is a **session** with the same four beats as a phone call:

| Beat | Protocol event | Android host (later) | ASR |
|---|---|---|---|
| Start | `VoiceCallEvent.Started` | `VoiceInteractionSession.onShow` | `startListening` |
| Partials | `VoiceCallEvent.Partial` | session UI text only | `onPartialResults` |
| Final | `VoiceCallEvent.Final` | commit utterance | `onResults` / segmented end |
| Hang up | `VoiceCallEvent.HungUp` | `onHide` / `finish` | `stopListening` / `cancel` |

The session MAY stay **live** across several finals (continuous / live-caption style). Hang up ends the call. A final is one committed utterance, not the end of the call.

```
IDLE ──Started──► LIVE ──HungUp──► HUNG_UP
                    │
                    ├── Partial (preview only)
                    ├── Final   → IngressEvent(source=VOICE) → expand → present
                    └── Failed  → FAILED
```

### 1.1 What MUST / MUST NOT happen

- **MUST** feed **final** (or user-confirmed) transcripts into the same `PolyglosEngine.expand` path as Share and `PROCESS_TEXT`.
- **MUST NOT** call `expand` or any presenter on partials.
- **MUST NOT** write partials to the clipboard or fire result notifications.
- **MUST NOT** read the clipboard as voice input (clipboard **input** is being removed). Clipboard **output** of a finished expand remains allowed — same as read-only `PROCESS_TEXT`.
- **MUST NOT** require TTS, cloud ASR, or Accessibility as the speech path.
- Accessibility MAY only be a fallback for *showing* the user’s own call UI, never for capturing other apps’ text as “voice”.

### 1.2 Ingresses (authoritative)

| Ingress | How text arrives | Today |
|---|---|---|
| Share | `ACTION_SEND` `text/plain` | `ShareTargetActivity` |
| `PROCESS_TEXT` | selection toolbar | `ProcessTextActivity` |
| Voice | `VoiceCallEvent.Final` → `IngressEvent` | protocol types only |

Clipboard assist / copy-hook is **not** an ingress going forward. Do not add `CLIPBOARD` to `IngressSource`.

---

## 2. Smallest OS-hostable call

Later Android assistive software hosts this same protocol. The in-app Try-it mic (when it lands) is a **host**, not a second product.

### 2.1 `VoiceInteractionService` / `VoiceInteractionSession`

When implementation is unblocked, register a `VoiceInteractionService` whose session **is** the call:

1. User invokes the assistant (long-press home / nav corner / RoleManager assistant / in-app Start).
2. `VoiceInteractionSession.onShow` → `Started` (show a small live UI: listening, partial text, hang up).
3. Partials update that UI.
4. Each `Final` becomes `IngressEvent(VOICE)` and runs expand + present (notification / clipboard **output** / overlay — existing presenter).
5. Hang up → `onHide` / `finish`; cancel recognition; no extra present.

Assist `AssistStructure` / screenshot (`onHandleAssist`, `onHandleScreenshot`) is **out of the first call**. Those are later-OS “what is on screen” assists. The 1.0 voice job is the user’s **own speech**, not scraping the host app.

Do **not** register the service in the manifest until the voice milestone: exporting `BIND_VOICE_INTERACTION` makes Πολγζλοξ an assistant candidate on GrapheneOS and fights Bixby on Note9.

### 2.2 On-device `SpeechRecognizer` / `RecognitionService`

Preferred backends (first that is **offline** for the active locale):

1. Platform `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE` (API 23+). On API 31+ prefer `createOnDeviceSpeechRecognizer` / `isOnDeviceRecognitionAvailable` when the OEM actually ships a recognizer.
2. **App-owned** `RecognitionService` + bundled or user-downloaded model (GrapheneOS default path — see §4).
3. Explicit user opt-in “Allow online speech recognition” (default **OFF**). MUST NOT be silent.

`VoicePlatformProfile.forSdk(sdk)` gates APIs that do not exist on Note9. Capability **API** ≠ capability **present**: GrapheneOS Pixel 8 has the API 31+ methods and still has no Google `RecognitionService`.

### 2.3 Live-caption style (continuous)

This means **our** call stays in `LIVE` and keeps emitting partials + finals, like Live Caption’s UX. It does **not** mean hooking the system Live Caption pipeline (not a public app API).

| SDK | How to keep the call live |
|---|---|
| 33+ | `RecognizerIntent.EXTRA_SEGMENTED_SESSION` (and related extras) when the backend supports it |
| 29 (Note9) | After each `onResults`, if still `LIVE`, `startListening` again. Treat `onEndOfSpeech` as a pause, not hang up |

Restart-on-final is the Note9-compatible continuous call. Segmented session is an optimization on later OS.

### 2.4 Assistant role (later OS)

- API 29+: `RoleManager.ROLE_ASSISTANT` exists (Note9 included).
- GrapheneOS / AOSP: Settings → default digital assistant can be a third-party `VoiceInteractionService`.
- Samsung Note9: Bixby usually holds the slot; the in-app host MUST work **without** winning the assistant role.
- Do not prompt for the assistant role on first expand. Offer it from Settings when the service is actually registered.

---

## 3. Code seam (what exists now)

Pure JVM in `domain` (no Android types):

- `IngressSource` — `PROCESS_TEXT`, `SHARE`, `VOICE`
- `IngressEvent` — `{ source, text, localeHint? }`
- `PolyglosEngine.expand(event, settings)` — **ignores** `source` for linguistics; presenters MAY log it
- `VoiceCallEvent` / `VoiceCallPhase` / `VoiceCallStateMachine` — start / partial / final / hang up
- `VoiceCall.toIngressEvent(Final)` — only finals become ingress
- `VoicePlatformProfile.forSdk` — API 29 vs 31 vs 33 gates

App (`com.japanglify.app.voice`): `VoiceCallHost` / `VoiceCallController` / `VoiceExpandSink` interfaces. No `Service` subclass, no manifest.

Share and `PROCESS_TEXT` MAY wrap `IngressEvent` when those files are next touched. This change MUST NOT rewrite those activities.

---

## 4. Device constraints (Note9 / 9185W / GrapheneOS)

`minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`. Pool: Galaxy Note9 (API 29, USB serial `27841130ae1c7ece`), 9185W (wireless-debug pool device), GrapheneOS Pixel 8.

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

### 4.2 9185W (wireless pool)

Used as the wireless-debug DUT in UAT (`docs/agent-handoff.md`). Exact `ro.build.version.sdk` is **not** recorded in-tree as of this addendum.

Until a UAT note records SDK + whether a `RecognitionService` is installed:

- MUST NOT assume API 31+ on-device recognizer APIs.
- MUST run the same `VoicePlatformProfile.forSdk` branch as Note9 when `sdk < 31`.
- Wireless debugging drops; voice UAT SHOULD prefer USB for the first call milestone.

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

---

## 5. Present after a final

Same presenters as Share / read-only `PROCESS_TEXT`:

- Clipboard **output** of the expanded string is OK.
- Result notification / overlay is OK.
- Partials: in-call text view only.

`source=VOICE` is telemetry and UX copy (“Expand” still, not “voice convert”). Linguistics ignore it.

---

## 6. Phasing (voice-specific)

| Phase | Deliverable |
|---|---|
| **Now** | This doc + protocol types + domain tests |
| **P3a** | In-app host implementing `VoiceCallController` (Try-it / a session activity) |
| **P3b** | Offline ASR backend behind `AsrBackend`; GrapheneOS-usable `RecognitionService` |
| **P4** | Exported `VoiceInteractionService` + session UI; optional `ROLE_ASSISTANT` |
| **Not Note9** | `createOnDeviceSpeechRecognizer`, segmented extras, microphone FGS — gated, never required |

Full feature code stays deferred until an explicit ticket unblocks P3a. The protocol types are the exception.

---

## 7. Document history

| Date | Change |
|---|---|
| 2026-09-21 | Live-call addendum: VoiceInteractionSession mapping, offline ASR, GrapheneOS / Note9 / 9185W gates |
