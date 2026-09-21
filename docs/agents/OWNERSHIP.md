# File ownership — 2026-09-21 parallel workers

Each specialist runs in an **isolated git worktree**. Edits do not land
on `agent/polyglos-1.0-core` until the conductor merges them.

Still keep a primary output file so merges are easy:

## Exclusive writes (this round)

| Owner | May write | Must not write |
|---|---|---|
| **Conductor (this session)** | `PolyglosEngine.kt`, `AnalyzerRegistry.kt`, `WhitespaceTokenizer.kt`, `PolyglosSettingsRoutingTest.kt`, `REQUIREMENTS.md` §7 mixed-input, clipboard-input removal later | voice design, logo assets |
| **Linguist** | `linguist-inbox.md`, `SOURCES.md` (facts only), optional *new* test file `MixedScriptPassthroughTest.kt` if a bug is not already covered | Engine/registry/tokenizer already edited by conductor; `REQUIREMENTS.md`; `VOICE_*`; `app/` |
| **Voice** | `VOICE_AND_PIPELINE.md`, optional `docs/polyglos/VOICE_CALL.md`, `voice-inbox.md` | `domain/…/polyglos/*Engine*`, clipboard, Settings |
| **Design researcher** | `design-research-notes.md` only | Any `.kt`, `REQUIREMENTS.md`, inboxes of others |
| **Refinement sync** | Isolated worktree: `sync-log.md`, REQUIREMENTS alignment after mixed-input commit (`d35b8c8`), `AnalyzerRegistry` KDoc only | Engine/tokenizer/settings tests; voice design; logo assets |

## Shared reads

`ARCHITECTURE.md`, `AGENTS.md`, GitHub issues 5, 6, 7, **13** (logo/settings), PRs 10–11, device notes.

## Collision already in flight

Conductor already has uncommitted edits on the mixed-input path. Linguist
and sync were prompted in ways that could retouch those files. They must
stop at docs/inbox if they have not written code yet.

## Gradle

One `:domain:test` may be running on QODESH. Do not start a second Gradle
daemon job on this tree until it finishes.
