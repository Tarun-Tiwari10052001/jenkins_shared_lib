# GitFlow CI/CD Pipeline — Jenkins Shared Library + Jenkinsfile

A build-once/promote-many CI/CD pipeline for a Java microservice, implemented
as a Jenkins **Shared Library** consumed by a thin, declarative **Jenkinsfile**.

## Layout

```
jenkinsfile-repo/
  Jenkinsfile                     # lives at the root of the microservice repo

shared-library/                   # a SEPARATE repo, registered in Jenkins as
  vars/                           # "Manage Jenkins > System > Global Pipeline
    configureBranchTriggers.groovy    Libraries" -> name: company-shared-library
    checkoutCode.groovy
    preCommitChecks.groovy
    prValidation.groovy
    codeReviewGate.groovy
    sonarAnalysis.groovy
    dependencyCheck.groovy
    gitleaksScan.groovy
    mavenBuildTest.groovy
    dockerBuildSbom.groovy
    trivyImageScan.groovy
    resolveExistingImage.groovy
    resolvePromotionSourceTag.groovy
    signAndPushArtifact.groovy
    argoDeploy.groovy
    dastScan.groovy
    integrationTests.groovy
    securityComplianceGate.groovy
    verifyArtifact.groovy
    monitoringAnnotate.groovy
    tagRelease.groovy
    semVer.groovy
    imageTag.groovy
    withVaultSecrets.groovy
    notifyTeam.groovy
  src/org/company/pipeline/
    GitFlowUtils.groovy            # branch-classification helpers
    Notifier.groovy                # notification helper (unit-testable POGO)
  resources/org/company/pipeline/
    argocd-app-template.yaml       # reference GitOps Application shape
```

## GitFlow branch behavior

| Branch      | Trigger                              | CI stages run                                            | Deploy target(s)         |
|-------------|---------------------------------------|------------------------------------------------------------|---------------------------|
| `feature/*` | GitHub webhook (`GenericTrigger`)     | Pre-commit, PR validation, code review, Sonar, SCA, secrets, build/test, Docker+SBOM, image scan, sign+push | Dev (ephemeral preview) + DAST/integration |
| `develop`   | Nightly cron                          | Same as above (no PR-only stages)                          | Dev + DAST/integration    |
| `hotfix/*`  | GitHub webhook (`GenericTrigger`)     | Same as feature, then skips Dev                             | QA/Staging (approval) → Compliance gate → Verify → Production (approval) |
| `release/*` | **Manual or Git-tag build only**      | **No rebuild** — promotes the existing `develop-*` image, re-scans it | QA/Staging (approval) → Compliance gate → Verify → Production (approval), then Git tag created |
| `master`    | **Manual or Git-tag build only**      | **No rebuild** — promotes the existing `develop-*` image, re-scans it | Compliance gate → Verify → Production (approval) |

This satisfies **build-once/promote-many**: only `feature/*`, `develop`, and
`hotfix/*` ever run `mvn package` / `docker build`. `release/*` and `master`
pull that exact image by digest/tag from Artifactory, retag it
(`myapp:release-v1.2.3`, `myapp:v1.2.3`), re-scan it, and deploy it — they
never recompile the application.

## Image/artifact tagging convention

| Branch      | Example tag              |
|-------------|---------------------------|
| `feature/x` | `myapp:feature-x-142`     |
| `develop`   | `myapp:develop-482`       |
| `hotfix/y`  | `myapp:hotfix-y-9`        |
| `release/*` | `myapp:release-v1.2.3`    |
| `master`/tag| `myapp:v1.2.3`            |

Semantic version numbers come from the release/tag name (`imageTag.groovy` /
`semVer.groovy`); non-release builds use the build number instead of a
version, since they are not release candidates.

## Required Jenkins plugins

- Pipeline: GitHub / Multibranch Pipeline (with **Discover branches** and
  **Discover tags** behaviors enabled on the job — tags are what let
  `release/*`/`master` be built "by Git tag")
- Generic Webhook Trigger (for `GenericTrigger` on `feature/*` / `hotfix/*`)
- SonarQube Scanner for Jenkins
- HashiCorp Vault Plugin (`withVault`)
- JUnit, JaCoCo, Dependency-Check plugins
- ansiColor, Timestamper, Workspace Cleanup

## Required tooling on build agents

`docker`, `maven` (JDK 17), `trivy`, `gitleaks`, `syft`, `cosign`,
`jf` (JFrog CLI), `argocd` CLI, `jq`, `newman`, `git`, `gh` (GitHub CLI).

## Credentials & secrets

**No secret values are hard-coded anywhere in the Jenkinsfile or library.**
Everything is fetched at the point of use from HashiCorp Vault via
`withVaultSecrets` (see `vars/withVaultSecrets.groovy`), using a single
Jenkins credential (`vault-approle`, an AppRole `role_id`/`secret_id` pair)
to authenticate to Vault itself. Vault keys used: `artifactory_user`,
`artifactory_pass`, `sonar_token`, `cosign_password`, `argocd_token`,
`grafana_api_key`, `github_api_token`, `github_push_token`.

## Security & compliance controls

- **SonarQube** quality gate (blocking) — `sonarAnalysis.groovy`
- **OWASP Dependency-Check** + **Trivy** (fs mode) for SCA — `dependencyCheck.groovy`
- **Gitleaks** secrets scan (blocking on any finding) — `gitleaksScan.groovy`
- **Trivy** (image mode) container scan, run at build time AND again after
  promotion — `trivyImageScan.groovy`
- **SBOM** generation (CycloneDX via `syft`) — `dockerBuildSbom.groovy`
- **cosign** image signing + verification before Production —
  `signAndPushArtifact.groovy` / `verifyArtifact.groovy`
- **Security & Compliance Gate**: automated zero-open-CRITICAL-CVE check +
  mandatory manual approval from `release-managers`/`security-team` before
  Production — `securityComplianceGate.groovy`

## Monitoring & logging integration

Every Argo CD deployment (`argoDeploy.groovy`) posts a **Grafana annotation**
marking the deployment event on dashboards, and sets `app`/`environment`/
`version` Helm values/pod labels + Prometheus scrape annotations (see
`resources/org/company/pipeline/argocd-app-template.yaml`) so Prometheus
metrics and Loki logs for the new release are immediately queryable and
correlated with the deployment.

## Why triggers are configured in script, not `triggers{}`

Declarative pipeline's `triggers { }` directive can't branch on
`env.BRANCH_NAME`. `configureBranchTriggers()` is called at plain script
scope, before `pipeline { }`, which Jenkins Multibranch jobs support because
`BRANCH_NAME` is available as soon as the lightweight Jenkinsfile checkout
happens — before the rest of the script executes. This keeps the
branch/trigger matrix in one DRY, testable library function instead of
duplicated per-branch job configuration.
