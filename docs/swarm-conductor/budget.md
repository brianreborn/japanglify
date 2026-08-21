# Fleet effort and model (SuperGrok window)

Two knobs, GitHub as the mailbox. Agents do **not** scrape the SuperGrok meter.

| Layer | What | Where |
|---|---|---|
| **Request** | What the work wants | Issue label `effort:*` (and later a model if we pin one) |
| **Cap** | What this SuperGrok window will pay for | [budget.json](../japanglify/budget.json) |

**Effective** = the lowest of: issue request, fleet `cap.effort`, `perRole[role].effort`. Same for model if set.

`scripts/swarm_budget.py` is the only matcher. `--effort` / model flags must be on **process start** (Null start / Normal start). A cap change does not `/effort` a running session.

## Window

SuperGrok resets **weekly, Friday, America/Los_Angeles**. Owner watches the meter. To burn a window: set `cap.effort` (and optional `cap.model`) and `cap.until` to the reset instant. When `until` is in the past, the fleet cap is **off** (host default medium still applies). `until: null` means the cap is standing.

## Who may write the cap

Owner, by editing `docs/japanglify/budget.json` on `main`. Not a Grok chat. Not a screenshot. A `/cap` command is future; do not invent one in YAML yet.

Cloud automations (intake, PR-follow, UAT-complete) stay at `perRole` **low** even when the bench is xhigh. GitHub Actions watchdog is not a Grok wallet.

## CLI (PowerShell and sh)

From the clone that has `scripts/swarm_budget.py` and `docs/swarm-conductor/prompt-bench.md`. Set `ISSUE_EFFORT` to the issue label, or leave it empty (unlabeled → medium request).

Prints `--effort …` / `--model …` only when they differ from the host default (medium / unset). Empty stdout → omit the flags.

**PowerShell** (`py -3`; SHALOM):

```powershell
git pull origin main
$issue = $env:ISSUE_EFFORT   # e.g. 'xhigh' or ''
$b = @('scripts/swarm_budget.py', '--role', 'swarm-bench', '--argv')
if ($issue) { $b = @('scripts/swarm_budget.py', '--role', 'swarm-bench', '--issue-effort', $issue, '--argv') }
$flags = @(((py -3 @b | Out-String).Trim() -split '\s+' | Where-Object { $_ }))
# Null start (Restore):
grok @flags (Get-Content -Raw docs/swarm-conductor/prompt-bench.md)
# Normal start (healthy session), instead of the line above:
# grok @flags --resume
```

**sh** (bash / git-bash / zsh):

```sh
git pull origin main
# optional: ISSUE_EFFORT=xhigh
if [ -n "${ISSUE_EFFORT:-}" ]; then
  flags=$(python3 scripts/swarm_budget.py --role swarm-bench --issue-effort "$ISSUE_EFFORT" --argv)
else
  flags=$(python3 scripts/swarm_budget.py --role swarm-bench --argv)
fi
# Null start (Restore). $flags is unquoted on purpose (word-split).
grok $flags "$(< docs/swarm-conductor/prompt-bench.md)"
# Normal start (healthy session), instead of the line above:
# grok $flags --resume
```

Do not use `-p` / `--print` / `--single` (those exit). Do not pass `--resume` on Restore. Do not pass `--continue` / `-c`.

## Never

- Debate remaining weekly % in a Conductor thread
- Invent `grokCredits` remaining
- Mid-session `/effort` because the cap moved — `/quit` and start again
- Raise an issue’s label to dodge the cap (the script still clamps)
