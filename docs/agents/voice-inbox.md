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

## 2026-09-21 — done (isolated worktree)

Finished the live-call design that a previous shared-tree draft left as
call-beats only.

- `docs/polyglos/VOICE_CALL.md` — start / partials / final / hang up;
  VoiceInteractionSession mapping without a manifest dump; Note9 API 29
  vs 9185W Android 15 vs GrapheneOS Pixel 8.
- `docs/polyglos/VOICE_AND_PIPELINE.md` — clipboard INPUT is not an
  ingress; MVP is the live call, not a one-shot in-app mic.
- Protocol types in `domain/…/polyglos/voice/` (compile + tests). Did
  not edit `PolyglosEngine` mixed-input code.
