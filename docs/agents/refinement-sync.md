# Refinement sync

**Role:** Keep every specialist worker on the same product state.
Circulate refinements. **Do not add significant new features.**

**Workers**
- Linguist consultant (`linguist-inbox.md`)
- Voice ingress (`voice-inbox.md`)
- Design researcher (`design-research-notes.md`)
- Coders on `agent/polyglos-1.0-core` / PR #11

**Shared state (authoritative today)**
1. Mixed **input** is accepted; mixed-script **words** pass through.
2. Clipboard **input** is being removed; clipboard **output** stays.
3. Ingress: Share, PROCESS_TEXT, later voice-as-live-call.
4. Every 1.0 language must work (ja, grc-ancient, el-modern, hbo, he-modern).
5. Devices: Note9, 9185W, GrapheneOS Pixel 8.
6. Open GitHub issues 5–7 plus logo/settings visual pass.

**Loop**
- Read all inboxes + notes + `docs/polyglos/REQUIREMENTS.md`.
- Copy durable refinements into REQUIREMENTS or SOURCES, not into new modules.
- Flag any worker that starts a new feature (voice *implementation* beyond
  the agreed call seam, new analyzers, Play publishing, TTS, etc.).
- Prefer polish: tests, passthrough, docs, hang fixes, Settings cruft,
  logo — not scope expansion.

**Output:** `docs/agents/sync-log.md`
