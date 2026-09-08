# Pipeline Stages → Tools Mapping

Each row is a stage in `Jenkinsfile`. "Runs on" reflects the `when {}` guard actually used
(all stages run for every branch unless noted).

| # | Stage | Purpose | Input | Output | Shared-lib call | Failure condition | Runs on |
|---|-------|---------|-------|--------|------------------|--------------------|---------|
| 1 | Checkout | Fetch source at the triggering commit | Git ref | Workspace + `env.GIT_SHA` | `checkout scm` | SCM unreachable / bad ref | all branches |
| 2 | Compute Version | Derive Maven version, Docker tag, optional Git tag | `env.BRANCH_NAME`, `pom.xml` version | `env.APP_VERSION`, `env.DOCKER_TAG`, `env.GIT_TAG` | `versionHelpers.resolve()` | Unparseable POM version | all branches |
| 3 | Build & Unit Tests | Compile + run unit tests | `pom.xml` | Compiled classes, surefire reports | `buildHelpers.buildJavaApp()` | Any test failure / compile error | all branches |
| 4 | Static Code Analysis | SAST + quality gate | Source tree | SonarQube analysis, gate status | `scanHelpers.runSonarQube()` | Quality gate status != `OK` | all branches |
| 5 | Dependency Scanning | SCA against 3rd-party libs | `pom.xml` deps | `dependency-check-report.*` | `scanHelpers.runDependencyCheck()` | Finding ≥ configured CVSS (default 9.0) | all branches |
| 6 | Secret Scanning | Detect committed credentials | Git history/diff | Gitleaks findings | `scanHelpers.runGitleaks()` | Any match (non-zero exit) | all branches |
| 7 | Build Container Image | Produce the immutable image ("build once") | `Dockerfile`, compiled app | Tagged local image | `buildHelpers.buildDockerImage()` | Docker build failure | all branches |
| 8 | Image Scanning | Vulnerability scan of the built image | Local image | Trivy findings | `scanHelpers.runTrivyScan()` | CRITICAL/HIGH finding | all branches |
| 9 | SBOM Generation | Software bill of materials | Local image | `sbom.json` (CycloneDX), archived | `scanHelpers.generateSBOM()` | Syft execution error | all branches |
| 10 | Sign Artifacts | Integrity/provenance | JAR + image | `.asc` signature, cosign signature | `deployHelpers.signWithGpg/signWithCosign()` | Signing key unavailable | `main`, `release/*`, `hotfix/*` only |
| 11 | Push to Artifactory | Publish immutable artifacts | JAR, image | Artifactory build-info, pushed image | `deployHelpers.pushMavenArtifacts/pushDockerImage()` | Upload/auth failure | all branches |
| 12 | Tag Release | Immutable Git reference for the release | `env.GIT_TAG` | Pushed annotated tag | `versionHelpers.tagRelease()` | Tag already exists / push rejected | when `GIT_TAG` set (`main`, `hotfix/*`) |
| 13 | Deploy to Dev | Promote (no rebuild) to `dev` | Image tag | Updated `dev/values.yaml`, Argo sync | `deployHelpers.promoteViaGitOps()` + `syncArgoApp()` | Argo sync failure / unhealthy app | `feature/*`, `develop` |
| 14 | Deploy to Staging | Promote to `staging` | Image tag | Updated `staging/values.yaml`, Argo sync | same as above | Argo sync failure | `release/*` |
| 15 | Approve Production Deploy | Human gate before prod | — | Approval/reject | `input` step | Rejected / timed out | `main`, `hotfix/*` |
| 16 | Deploy to Prod | Promote to `prod` | Image tag | Updated `prod/values.yaml`, Argo sync | same as above | Argo sync failure / unhealthy app | `main`, `hotfix/*` |

## Post-build actions
| Condition | Action |
|---|---|
| `failure` | `notifySlack(color: 'danger', ...)` to `#ci-cd-alerts` |
| `success` | `notifySlack(color: 'good', ...)` with resolved version |

## Notes on "build once, deploy anywhere"
Stages 1–11 run exactly once per commit and produce one immutable JAR + one immutable image.
Stages 13/14/16 never re-invoke Maven or `docker build` — they only rewrite the target
environment's `values.yaml` in the GitOps config repo and ask Argo CD to sync, which is what
guarantees the same bits that passed security scanning are what reaches production.
