# Polyglos specialist agents

Mailbox + briefs for long-lived consultants. Coders file questions in
the matching inbox; idle work is defined in each brief.

| Agent | Brief | Inbox | Idle work |
|---|---|---|---|
| Linguist consultant | [linguist-consultant.md](./linguist-consultant.md) | [linguist-inbox.md](./linguist-inbox.md) | Audit romanizers, lexica, script detection for inconsistencies |
| Voice ingress | [voice-ingress.md](./voice-ingress.md) | [voice-inbox.md](./voice-inbox.md) | Live “call” / VoiceInteraction / later Android assist APIs |
| Design researcher | [design-researcher.md](./design-researcher.md) | [design-research-notes.md](./design-research-notes.md) | High-effort review of eventual issues, not near-term bugs |
| Refinement sync | [refinement-sync.md](./refinement-sync.md) | [sync-log.md](./sync-log.md) | Align workers; polish only, no new features |

ADB UAT hosts: take `lockf` on `$HOME/$ADBUUID` before using a phone — see [adb-uat-lock.md](./adb-uat-lock.md).

Do not implement from an inbox note until a coder (or the user) asks.
The linguist does not land code unless a table/test is the smallest
proof of a linguistic bug.
