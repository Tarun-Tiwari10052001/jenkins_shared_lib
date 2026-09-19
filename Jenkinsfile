// Jenkinsfile — placed at the root of the Java microservice repository.
// All heavy logic lives in the "company-shared-library" Shared Library
// (see shared-library/ in this bundle) so this file stays thin and DRY.
//
// Required Jenkins configuration (see README.md for full details):
//   - Manage Jenkins > System > Global Pipeline Libraries: name "company-shared-library"
//   - Multibranch Pipeline job with "Discover branches" + "Discover tags" behaviours
//   - Credentials: vault-approle, github-api-token, cosign-signing-key (referenced by ID only)
//   - Agents labelled "docker && maven" with: docker, maven/jdk17, trivy, gitleaks,
//     syft, cosign, jfrog-cli (jf), argocd-cli, jq, newman, git

@Library('my-shared-lib@dev') _

// ---------------------------------------------------------------------------
// Branch-specific triggers (must run at script scope, before the pipeline{}
// block, because the declarative `triggers` directive cannot branch on
// env.BRANCH_NAME). Delegated to the shared library to keep this file DRY.
//   - feature/*  -> GitHub webhook (GenericTrigger) on push
//   - hotfix/*   -> GitHub webhook (GenericTrigger) on push
//   - develop    -> nightly cron
//   - release/*  -> NONE (manual build or Git-tag build only)
//   - master     -> NONE (manual build or Git-tag build only)
// ---------------------------------------------------------------------------
configureBranchTriggers(env.BRANCH_NAME)

pipeline {

    agent { label 'docker && maven' }

    parameters {
        string(
            name: 'PROMOTE_FROM_TAG',
            defaultValue: '',
            description: 'For release/* and master builds ONLY: the already-built develop image tag ' +
                         'to promote (e.g. "develop-482"). Leave blank to auto-resolve the latest ' +
                         'develop build from Artifactory. Implements build-once/promote-many.'
        )
        booleanParam(
            name: 'SKIP_DAST',
            defaultValue: false,
            description: 'Skip DAST scanning. Emergency use only — requires justification in the build cause.'
        )
    }

    options {
        timestamps()
        // ansiColor('xterm')
        buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '15'))
        disableConcurrentBuilds()
        timeout(time: 2, unit: 'HOURS')
        skipDefaultCheckout(false)
    }

    environment {
        APP_NAME        = 'myapp'
        DOCKER_REGISTRY = 'artifactory.company.com/docker-local'
        ARTIFACTORY_URL = 'https://artifactory.company.com/artifactory'
        ARGOCD_SERVER   = 'argocd.company.com'
        GRAFANA_URL     = 'https://grafana.company.com'
        VAULT_ADDR      = 'https://vault.company.com:8200'
    }

    stages {

        stage('Checkout') {
            steps {
                script { checkoutCode() }
            }
        }

        stage('Compute Version & Tag') {
            steps {
                script {
                    env.IMAGE_TAG = imageTag()
                    echo "Branch '${env.BRANCH_NAME}' -> resolved artifact/image tag: ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Pre-Commit Checks') {
            when { not { anyOf { branch 'release/*'; branch 'master' } } }
            steps { script { preCommitChecks() } }
        }

        stage('PR Validation & Code Review') {
            when { anyOf { branch 'feature/*'; branch 'hotfix/*' } }
            steps {
                script {
                    prValidation()
                    codeReviewGate()
                }
            }
        }

        stage('Static & Security Analysis') {
            // SonarQube + OWASP Dependency-Check + Trivy (fs) + Gitleaks
            when { not { anyOf { branch 'release/*'; branch 'master' } } }
            steps {
                script {
                    sonarAnalysis()
                    dependencyCheck()
                    gitleaksScan()
                }
            }
        }

        stage('Build & Unit Test') {
            when { not { anyOf { branch 'release/*'; branch 'master' } } }
            steps { script { mavenBuildTest() } }
        }

        stage('Docker Build, SBOM & Image Scan') {
            when { not { anyOf { branch 'release/*'; branch 'master' } } }
            steps {
                script {
                    dockerBuildSbom(env.IMAGE_TAG)
                    trivyImageScan(env.IMAGE_TAG)
                }
            }
        }

        stage('Promote Existing Build (build-once/promote-many)') {
            // release/* and master NEVER rebuild from source. They resolve and
            // retag the artifact/image that was already built + scanned on
            // develop (or on the hotfix branch itself), then re-scan the final
            // tag as a compliance re-check before it moves further.
            when { anyOf { branch 'release/*'; branch 'master' } }
            steps {
                script {
                    def sourceTag = resolvePromotionSourceTag(params.PROMOTE_FROM_TAG)
                    resolveExistingImage(sourceTag)
                    trivyImageScan(env.IMAGE_TAG)
                }
            }
        }

        stage('Sign & Push to Artifactory') {
            steps { script { signAndPushArtifact(env.IMAGE_TAG) } }
        }

        stage('Deploy to Dev (Argo CD)') {
            when { anyOf { branch 'develop'; branch 'feature/*' } }
            steps { script { argoDeploy('dev', env.IMAGE_TAG) } }
        }

        stage('DAST & Integration Tests (Dev)') {
            when {
                allOf {
                    anyOf { branch 'develop'; branch 'feature/*' }
                    expression { return !params.SKIP_DAST }
                }
            }
            steps {
                script {
                    dastScan('https://dev.myapp.company.com')
                    integrationTests('https://dev.myapp.company.com')
                }
            }
        }

        stage('Deploy to QA/Staging (Argo CD)') {
            when { anyOf { branch 'release/*'; branch 'hotfix/*' } }
            steps {
                input message: 'Approve deployment to QA/Staging?', ok: 'Deploy to QA',
                      submitter: 'qa-team,release-managers'
                script {
                    argoDeploy('qa', env.IMAGE_TAG)
                    dastScan('https://qa.myapp.company.com')
                    integrationTests('https://qa.myapp.company.com')
                }
            }
        }

        stage('Security & Compliance Gate') {
            when { anyOf { branch 'release/*'; branch 'master'; branch 'hotfix/*' } }
            steps { script { securityComplianceGate() } }
        }

        stage('Verify Artifact/Image') {
            when { anyOf { branch 'release/*'; branch 'master'; branch 'hotfix/*' } }
            steps { script { verifyArtifact(env.IMAGE_TAG) } }
        }

        stage('Deploy to Production (Argo CD)') {
            when { anyOf { branch 'release/*'; branch 'master'; branch 'hotfix/*' } }
            steps {
                input message: "Deploy ${env.APP_NAME}:${env.IMAGE_TAG} to PRODUCTION?",
                      ok: 'Deploy to Prod', submitter: 'release-managers'
                script { argoDeploy('prod', env.IMAGE_TAG) }
            }
        }

        stage('Tag Release in Git') {
            when { branch 'release/*' }
            steps { script { tagRelease(env.IMAGE_TAG) } }
        }
    }

    post {
        success {
            script { notifyTeam(":white_check_mark: ${env.APP_NAME} ${env.IMAGE_TAG} — pipeline succeeded on ${env.BRANCH_NAME} (#${env.BUILD_NUMBER})", 'good') }
        }
        failure {
            script { notifyTeam(":x: ${env.APP_NAME} — pipeline FAILED on ${env.BRANCH_NAME} (#${env.BUILD_NUMBER}). Check Sonar/Trivy/Gitleaks/ZAP reports.", 'danger') }
        }
        unstable {
            script { notifyTeam(":warning: ${env.APP_NAME} — pipeline UNSTABLE on ${env.BRANCH_NAME} (#${env.BUILD_NUMBER}).", 'warning') }
        }
        always {
            cleanWs()
        }
    }
}
