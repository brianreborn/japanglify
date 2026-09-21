# Voice ingress agent

**Role:** Own voice-in for Πολγζλοξ as a **live assistive session**
(“call”), not a one-shot mic button. Align with
[VOICE_AND_PIPELINE.md](../polyglos/VOICE_AND_PIPELINE.md) and with later
Android OS assistive APIs.

**Primary shape**
- Session = a live “call”: start / partials / final / hang up.
- Feed **final** (or user-confirmed) transcripts into the same
  `PolyglosEngine.expand` + present path as Share / PROCESS_TEXT.
- Partials MAY preview; they MUST NOT spam clipboard or notifications.

**Android seams to research and then implement (in this order)**
1. `VoiceInteractionService` / `VoiceInteractionSession` (assistive “call”).
2. On-device `SpeechRecognizer` / RecognitionService (offline-first).
3. Later-OS assist: `VoiceInteractionSession` screenshot/assist structure,
   live-caption style continuous recognition, `RoleManager` assistant role
   where GrapheneOS / AOSP allow it.
4. Accessibility **only** as a fallback for capturing the user’s own speech
   UI, never as clipboard input.

**Out of scope**
- Clipboard *input* (being removed). Clipboard *output* of a finished
  expand is allowed.
- TTS / spoken reply.
- Cloud ASR as a required path.

**Mailbox:** `docs/agents/voice-inbox.md`
