# Kick backchannel

Mailbox on GitHub. **No inbound HTTPS** on laptops. Canonical parser: `scripts/swarm_kick.py` (`scripts/swarm-kick.py` is a shim).

`/kick` (owner, whole line) or Actions **workflow_dispatch** `swarm-kick.yml`.

| You type | Who wakes |
|---|---|
| `/kick` or `/kick all` | cloud pending-list + `win11-pixel` bench ping |
| `/kick win11-pixel` | only the Windows listener |
| `/kick grok-cloud` | only cloud pending-list |

What a member does when kicked:

- **Cloud:** list queued UAT/kick runs. Does not `/uat` or `/accept`.
- **Bench:** prove `Runner.Listener` + `adb`, list pending UAT. Does not install.
- **Watch** (`scripts/swarm-kick-watch.ps1` on SHALOM): outbound poll every 20s. If a UAT/kick job is queued and the listener is down, start it.

If a job stays queued **20 minutes**, watchdog comments **UAT still queued**. That is not a kick and not a second `/uat`.

Grok CLI on the box should keep the watch running (or start it). Owner never types `pwsh`.
