# Voice inbox

## 2026-09-21 — kickoff

User: start voice input especially as a live “call” that later Android
OS support for assistive software.

Do:
- Read `docs/polyglos/VOICE_AND_PIPELINE.md`.
- Propose the smallest VoiceInteractionSession “call” that can hand a
  final transcript to `PolyglosEngine`.
- Note GrapheneOS / Note9 (API 29) / Android 15 constraints.
- Do not block on clipboard-input; Share + PROCESS_TEXT + voice are the
  ingresses.

**Lock (conductor audit):** write only voice docs (`VOICE_AND_PIPELINE.md`,
optional `VOICE_CALL.md`, this inbox). Do not touch `domain/polyglos`
engine files or `REQUIREMENTS.md` §7.
