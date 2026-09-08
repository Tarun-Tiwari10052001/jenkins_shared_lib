@Library('my-shared-lib@main') _
// Loads: vars/buildHelpers, vars/scanHelpers, vars/deployHelpers, vars/versionHelpers, vars/notifySlack
import org.company.pipeline.Utility

def branchType   = Utility.branchType(env.BRANCH_NAME)
def isProtected  = Utility.isProtectedBranch(env.BRANCH_NAME)
def cfg          = readYaml text: libraryResource('org/company/pipeline/config.yml')

pipeline {
    agent any

    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timestamps()
        disableConcurrentBuilds()
    }

    triggers {
        // Feature branches / PRs: webhook only.
        // Develop: webhook + nightly cron.
        // Release/main/hotfix: no automatic trigger (manual or tag-push kicks these off).
        GenericTrigger(
            genericVariables: [[key: 'ref', value: '$.ref']],
            token: 'WEBHOOK_TOKEN',
            regexpFilterText: '$ref',
            regexpFilterExpression: 'refs/heads/' + env.BRANCH_NAME
        )
        cron(env.BRANCH_NAME == 'develop' ? 'H 2 * * *' : '')
    }

    environment {
        VAULT_ADDR             = credentials('vault-addr')
        ARTIFACTORY_CREDENTIALS = credentials('artifactory-creds')
        REGISTRY               = 'myregistry'
        IMAGE_NAME             = 'myapp'
        CONFIG_REPO_DIR        = 'gitops-config'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                script { env.GIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim() }
            }
        }

        stage('Compute Version') {
            steps {
                script {
                    def v = versionHelpers.resolve(gitCommit: env.GIT_SHA)
                    env.APP_VERSION = v.mavenVersion
                    env.DOCKER_TAG  = v.dockerTag
                    env.GIT_TAG     = v.gitTag ?: ''
                    env.IS_RELEASE  = v.isRelease.toString()
                }
            }
        }

        stage('Build & Unit Tests') {
            steps {
                script {
                    buildHelpers.buildJavaApp(pom: 'pom.xml', mvnArgs: "-Drevision=${env.APP_VERSION}")
                }
            }
            post {
                always { junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true }
            }
        }

        stage('Static Code Analysis (SonarQube)') {
            steps {
                script { scanHelpers.runSonarQube(projectKey: 'my-app', serverName: cfg.sonarqube.serverName) }
            }
        }

        stage('Dependency Scanning (OWASP)') {
            steps {
                script { scanHelpers.runDependencyCheck(failOnCvssScore: cfg.dependencyCheck.failOnCvssScore) }
            }
        }

        stage('Secret Scanning (Gitleaks)') {
            steps {
                script { scanHelpers.runGitleaks(redact: cfg.gitleaks.redact) }
            }
        }

        stage('Build Container Image') {
            steps {
                script {
                    env.FQ_IMAGE_TAG = buildHelpers.buildDockerImage(
                        registry : env.REGISTRY,
                        imageName: env.IMAGE_NAME,
                        tag      : env.DOCKER_TAG
                    )
                    // On main/hotfix, also apply a floating semver-only tag alongside the immutable one.
                    if (isProtected && env.IS_RELEASE == 'true') {
                        buildHelpers.retagDockerImage(env.FQ_IMAGE_TAG, ["${env.REGISTRY}/${env.IMAGE_NAME}:${env.APP_VERSION}"])
                    }
                }
            }
        }

        stage('Image Scanning (Trivy)') {
            steps {
                script { scanHelpers.runTrivyScan(env.FQ_IMAGE_TAG, severity: cfg.trivy.severity) }
            }
        }

        stage('SBOM Generation (Syft)') {
            steps {
                script { scanHelpers.generateSBOM(env.FQ_IMAGE_TAG, cfg.sbom.outputFile) }
            }
        }

        stage('Sign Artifacts') {
            when { expression { isProtected } }
            steps {
                script {
                    deployHelpers.signWithGpg("target/${env.IMAGE_NAME}.jar")
                    deployHelpers.signWithCosign(env.FQ_IMAGE_TAG)
                }
            }
        }

        stage('Push to Artifactory') {
            steps {
                script {
                    deployHelpers.pushMavenArtifacts(
                        serverId    : cfg.artifactory.serverId,
                        mavenTool   : cfg.artifactory.mavenTool,
                        releaseRepo : cfg.artifactory.releaseRepo,
                        snapshotRepo: cfg.artifactory.snapshotRepo,
                        pom         : 'pom.xml'
                    )
                    deployHelpers.pushDockerImage(env.FQ_IMAGE_TAG,
                        dockerRepo : cfg.artifactory.dockerRepo,
                        buildName  : env.JOB_NAME,
                        buildNumber: env.BUILD_NUMBER
                    )
                }
            }
        }

        stage('Tag Release') {
            when { expression { env.GIT_TAG != '' } }
            steps {
                script { versionHelpers.tagRelease(env.GIT_TAG) }
            }
        }

        stage('Deploy to Dev') {
            when { anyOf { branch 'develop'; expression { branchType == 'feature' } } }
            steps {
                script {
                    checkoutConfigRepo()
                    deployHelpers.promoteViaGitOps(environment: 'dev', configRepoDir: env.CONFIG_REPO_DIR, imageTag: env.FQ_IMAGE_TAG)
                    deployHelpers.syncArgoApp(cfg.argocd.apps.dev)
                }
            }
        }

        stage('Deploy to Staging') {
            when { expression { branchType == 'release' } }
            steps {
                script {
                    checkoutConfigRepo()
                    deployHelpers.promoteViaGitOps(environment: 'staging', configRepoDir: env.CONFIG_REPO_DIR, imageTag: env.FQ_IMAGE_TAG)
                    deployHelpers.syncArgoApp(cfg.argocd.apps.staging)
                }
            }
        }

        stage('Approve Production Deploy') {
            when { expression { branchType == 'main' || branchType == 'hotfix' } }
            steps {
                input message: "Deploy ${env.APP_VERSION} (${env.FQ_IMAGE_TAG}) to PRODUCTION?", ok: 'Deploy'
            }
        }

        stage('Deploy to Prod') {
            when { expression { branchType == 'main' || branchType == 'hotfix' } }
            steps {
                script {
                    checkoutConfigRepo()
                    deployHelpers.promoteViaGitOps(environment: 'prod', configRepoDir: env.CONFIG_REPO_DIR, imageTag: env.FQ_IMAGE_TAG)
                    deployHelpers.syncArgoApp(cfg.argocd.apps.prod, wait: true, timeoutSeconds: 300)
                }
            }
        }
    }

    post {
        failure {
            script { notifySlack(color: 'danger', message: "Build ${env.JOB_NAME} #${env.BUILD_NUMBER} (${env.BRANCH_NAME}) FAILED", channel: cfg.notifications.slackChannel) }
        }
        success {
            script { notifySlack(color: 'good', message: "Build ${env.JOB_NAME} #${env.BUILD_NUMBER} (${env.BRANCH_NAME}) succeeded — ${env.APP_VERSION}", channel: cfg.notifications.slackChannel) }
        }
    }
}

/** Shallow-clone the GitOps config repo into CONFIG_REPO_DIR for the promotion step. */
def checkoutConfigRepo() {
    dir(env.CONFIG_REPO_DIR) {
        git branch: 'main', credentialsId: 'gitops-config-creds', url: 'https://git.company.com/platform/gitops-config.git'
    }
}
