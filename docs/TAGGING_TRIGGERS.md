# Tagging & Trigger Reference

## Branch patterns

| Branch Pattern | Purpose | Merges Into | Trigger |
|---|---|---|---|
| `feature/*` | Short-lived feature work | `develop` (via PR) | Webhook (`GenericTrigger`) on push/PR |
| `develop` | Integration branch | `release/x.y.z` when stabilized | Webhook + nightly cron (`H 2 * * *`) |
| `release/*` | Release stabilization, bugfixes only | `main` (tag) + back-merge `develop` | Manual / tag-push |
| `hotfix/*` | Emergency production fixes | `main` (tag) + back-merge `develop` | Manual, usually on PR creation |
| `main` | Always production-ready | — | Only on merge (no periodic build) |

## Version / tag scheme by workflow

| Workflow | Maven Version | Docker Tag | Git Tag |
|---|---|---|---|
| Feature branch | `1.3.0-SNAPSHOT` | `feature-<branch>-<sha7>` | — |
| Develop (nightly) | `1.3.0-SNAPSHOT` | `dev-<sha7>` | — |
| Release prep | `1.2.3-RC1` | `1.2.3-rc1` | `v1.2.3-rc1` |
| Merge to main | `1.2.3` | `1.2.3` (+ full-SHA variant) | `v1.2.3` (annotated) |
| Hotfix merge | `1.2.4` | `1.2.4` | `v1.2.4` |

Every build additionally pushes a full-SHA tag (`myapp:<version>-<full-sha>`) for traceability
regardless of branch.

## Trigger options considered

| Aspect | Chosen (Option A) | Alternative (Option B) | Why A |
|---|---|---|---|
| Artifact tagging | SemVer tags (`vX.Y.Z` on `main`) | Commit-based (`build-1234`) | Industry standard, human-readable |
| Image tagging | `registry/myapp:${version}` + `${gitSha}` | `latest` on every merge | Avoids ambiguous/mutable `latest` in prod |
| Feature triggers | Webhook (`GenericTrigger`) | SCM polling | Immediate, avoids polling load |
| Develop triggers | Cron (nightly) + webhook | SCM poll only | Combines timeliness with a safety-net schedule |
| Release promotion | Manual/parameterized build | Auto-build every commit | Human gate reduces release risk |
| Argo CD app model | App-per-environment (folder-per-env) | App-of-apps / env branches | Simpler, safer, no long-lived env branches |

## Branch protection

| Branch | Protection |
|---|---|
| `main` | Required PR review + passing Jenkins status checks; no direct commits; only `release/*`/`hotfix/*` merges allowed |
| `develop` | PR required, green build required |
| `feature/*`, `hotfix/*`, `release/*` | Unprotected (source branches) — gating happens at the PR into a protected branch |
| Tags | Creation restricted to CI service account / release-manager role |

## Sync policy by environment (Argo CD)

| Environment | Sync Policy | Rationale |
|---|---|---|
| `dev` | Automated (prune + self-heal) | Fast feedback, low risk |
| `staging` | Automated (prune + self-heal) | Mirrors prod behavior pre-release |
| `prod` | Manual | Requires the pipeline's `input` approval gate before `argocd app sync` |
