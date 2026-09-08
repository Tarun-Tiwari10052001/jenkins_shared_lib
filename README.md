# GitFlow CI/CD Pipeline — Jenkins + DevSecOps + Argo CD (GitOps)

This is a ready-to-adapt implementation of the CI/CD strategy: **GitFlow branching**, a
**Jenkins Shared Library** for DRY pipeline code, an integrated **DevSecOps** toolchain
(SonarQube, OWASP Dependency-Check, Gitleaks, Trivy, Syft, GPG/cosign), artifact promotion
through **JFrog Artifactory**, and **build-once/promote-everywhere** deployment via
**Argo CD** GitOps, observed with **Prometheus / Grafana / Loki**.

## Layout

```
jenkins-cicd-pipeline/
├── Jenkinsfile                     # Declarative, branch-aware multibranch pipeline
├── vars/                           # Shared Library global steps (importable as functions)
│   ├── buildHelpers.groovy / .txt
│   ├── scanHelpers.groovy   / .txt
│   ├── deployHelpers.groovy / .txt
│   ├── versionHelpers.groovy/ .txt
│   └── notifySlack.groovy   / .txt
├── src/org/company/pipeline/       # Shared Library classes
│   ├── Utility.groovy
│   └── VersionCalculator.groovy
├── resources/org/company/pipeline/
│   └── config.yml                  # Default pipeline config (tool endpoints, thresholds)
├── config-repo-example/            # Sample GitOps repo (Argo CD watches this, NOT the app repo)
│   ├── dev/myapp.yaml
│   ├── staging/myapp.yaml
│   └── prod/myapp.yaml
└── docs/
    ├── PIPELINE_STAGES.md          # Stage-by-stage: purpose, inputs/outputs, failure conditions
    ├── TAGGING_TRIGGERS.md         # Versioning, tagging, and trigger reference tables
    └── DIAGRAMS.md                 # Mermaid flow + sequence diagrams
```

## How the pieces fit together

1. **Shared Library** (`vars/`, `src/`) lives in its own SCM repo (e.g. `my-shared-lib`) and is
   registered under *Manage Jenkins → System → Global Pipeline Libraries*. Every
   microservice's `Jenkinsfile` loads it with:
   ```groovy
   @Library('my-shared-lib@main') _
   ```
2. **`Jenkinsfile`** is checked into every branch of the application repo. Jenkins Multibranch
   Pipeline auto-discovers branches containing a `Jenkinsfile` and builds them with
   branch-appropriate triggers/stages, driven off `env.BRANCH_NAME`.
3. **Artifacts** are built once (JAR + Docker image), scanned, signed, and pushed to
   Artifactory with immutable, traceable tags (see `docs/TAGGING_TRIGGERS.md`).
4. **Promotion** never rebuilds — it updates the image tag / Helm values in the
   `config-repo-example/<env>/` folder. Argo CD watches that repo and syncs the cluster to
   match Git (GitOps), so environment promotion is a Git operation, not a new build.
5. **Rollback** is `argocd app rollback myapp <history-id>` or a Git revert on the config repo.

## Setup checklist

- [ ] Register the Shared Library in Jenkins (name it `my-shared-lib`, point at this repo).
- [ ] Create Jenkins credentials: `vault-addr`, `vault-token-id`, `artifactory-creds`,
      `gpg-key`, `cosign-key`, `sonarqube-token`, `slack-webhook`.
- [ ] Configure the SonarQube server under *Manage Jenkins → System → SonarQube servers*
      (name it `SonarQubeServer` to match the `Jenkinsfile`).
- [ ] Point Artifactory repos: `libs-release-local`, `libs-snapshots-local`, `docker-local`.
- [ ] Create three Argo CD Applications (`myapp-dev`, `myapp-staging`, `myapp-prod`) pointing
      at the corresponding folders in your real GitOps config repo.
- [ ] Adjust `resources/org/company/pipeline/config.yml` thresholds (CVSS, coverage, etc.)
      to your org's policy.
- [ ] Set branch protection on `main` and `release/*` per `docs/PIPELINE_STAGES.md`.

Everything here is a template — placeholder credential IDs, hostnames, and Slack channels are
marked and should be swapped for your environment.
