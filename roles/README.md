# Role-host configuration

`roles/hosts/` contains committed profiles for known repository hosts. A profile
matches a hostname, workspace, and `origin` remote, then declares one primary
role and an explicit capability allowlist. A profile is disabled by default.

`roles/templates/` contains reusable role and capability definitions. Templates
describe permitted work; they do not authorize a worker assignment or model
configuration on their own.

`roles/self.json` is generated locally by `scripts/manage-role.ps1` and is
ignored by Git. It is the active profile projection for the current checkout.

## Dashboard gate

`scripts/ci-pipeline-dashboard.ps1` runs only when `roles/self.json` is an
enabled `orchestrator` profile that matches the current hostname, workspace,
and `origin` remote. All other hosts fail closed before the dashboard reads
GitHub or local process state.

## Local commands

```powershell
.\scripts\manage-role.ps1 status
.\scripts\manage-role.ps1 enable
.\scripts\manage-role.ps1 validate
.\scripts\manage-role.ps1 disable
```

Enabling a profile authorizes the local dashboard gate only. It does not grant
approval to spawn workers, change model configuration, write code, or publish
artifacts.
