# Fleet effort and model (SuperGrok window)

Two knobs, GitHub as the mailbox. Agents do **not** scrape the SuperGrok meter.

| Layer | What | Where |
|---|---|---|
| **Request** | What the work wants | Issue label `effort:*` (and later a model if we pin one) |
| **Cap** | What this SuperGrok window will pay for | [budget.json](../japanglify/budget.json) |

**Effective** = issue request, then min(fleet cap, per-role), then the host env band:

| Env | Meaning |
|---|---|
| `SWARM_EFFORT_MIN` | Floor for **every** agent on this machine (`low`/`medium`/`high`/`xhigh`) |
| `SWARM_EFFORT_MAX` | Ceiling for **every** agent on this machine |
| `SWARM_MODEL` | Optional model id (same as a fleet model cap) |

If min > max, **max wins** (wallet). Unset = no band. Cloud automations do not read these unless you set them there.

Windows cmd (this logon):

```bat
set SWARM_EFFORT_MIN=medium
set SWARM_EFFORT_MAX=high
```

Windows, persist for the user (next logon):

```bat
setx SWARM_EFFORT_MIN medium
setx SWARM_EFFORT_MAX high
```

Unix:

```sh
export SWARM_EFFORT_MIN=medium
export SWARM_EFFORT_MAX=high
```

PowerShell:

```powershell
$env:SWARM_EFFORT_MIN = "medium"
$env:SWARM_EFFORT_MAX = "high"
```

`swarm-grok` / `swarm_budget.py` read them at **process start**. Changing the env does not `/effort` a running session.


`scripts/swarm_budget.py` is the matcher. `scripts/swarm-grok.py` is the **one** start command (Windows cmd and Unix sh). `--effort` / model flags must be on **process start**. A cap change does not `/effort` a running session.

## Window

SuperGrok resets **weekly, Friday, America/Los_Angeles**. Owner watches the meter. To burn a window: set `cap.effort` (and optional `cap.model`) and `cap.until` to the reset instant. When `until` is in the past, the fleet cap is **off** (host default medium still applies). `until: null` means the cap is standing.

## Who may write the cap

Owner, by editing `docs/japanglify/budget.json` on `main`. Not a Grok chat. Not a screenshot. A `/cap` command is future; do not invent one in YAML yet.

Cloud automations (intake, PR-follow, UAT-complete) stay at `perRole` **low** even when the bench is xhigh. GitHub Actions watchdog is not a Grok wallet.

## CLI (one script, both OS)

From the clone that has `scripts/swarm-grok.py`. Do not use `-p`. Do not hand-roll `grok --effort`.

Windows **cmd.exe** (preferred over `.ps1`):

```bat
scripts\swarm-grok.cmd
scripts\swarm-grok.cmd --resume
scripts\swarm-grok.cmd --issue-effort xhigh
scripts\swarm-grok.cmd --resume --issue-effort xhigh
```

Unix **sh**:

```sh
./scripts/swarm-grok
./scripts/swarm-grok --resume
./scripts/swarm-grok --issue-effort xhigh
./scripts/swarm-grok --resume --issue-effort xhigh
```

Same Python on either (if you already have `python3` / `py -3` on PATH):

```text
python3 scripts/swarm-grok.py
python3 scripts/swarm-grok.py --resume --issue-effort xhigh
python3 scripts/swarm-grok.py --dry-run --no-pull
```

`ISSUE_EFFORT` / `ISSUE_MODEL` env vars work if you omit the flags. `--dry-run` prints argv and does not exec `grok`.

Null start slurps [prompt-bench.md](prompt-bench.md). `--resume` is a healthy continue (no slurp). Empty budget argv → no `--effort` (host default medium). Today’s cap clamps bench xhigh to medium.

## Never

- Debate remaining weekly % in a Conductor thread
- Invent `grokCredits` remaining
- Mid-session `/effort` because the cap moved — `/quit` and start again
- Raise an issue’s label to dodge the cap (the script still clamps)
- A PowerShell-only start; `swarm-grok.cmd` is the Windows entry
