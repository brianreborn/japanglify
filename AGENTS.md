# Agent operating instructions

## Windows Codex session preflight

Before GitHub, Android build, physical-device, CI-dashboard, or LAN-worker work
in a newly started Windows Codex container:

1. Read `docs/codex-session-permissions.md` completely.
2. Run `scripts/initialize-codex-session.ps1` and inspect every failed check.
   Use `-RequireDevice` when the task includes phone validation.
3. If shell network or a required filesystem root is unavailable, request the
   narrowest reusable session-scoped Codex permission and rerun the preflight.
4. Treat the preflight's workspace paths as authoritative; never copy GitHub,
   ADB, model-provider, or worker-host credentials into the repository.
5. When a new recurring permission is discovered, update both the permission
   document and the preflight output as part of the same change.

Technical access is capability, not authorization. It does not override the
approval boundaries documented in `docs/codex-session-permissions.md`,
including Brian's final approval of every exact worker provider/model/effort
configuration—including GitHub Copilot model, context, and CLI permission
mode—and the separate gates for releases, upstream writes, destructive
actions, and non-routine changes.
