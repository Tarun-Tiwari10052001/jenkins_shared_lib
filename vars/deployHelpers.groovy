// vars/deployHelpers.groovy
// Global step: deployHelpers.xxx(...)

/** Sign a JAR (or other file) with GPG. Expects a 'gpg-key' file credential and GPG_PASSPHRASE env/secret. */
def signWithGpg(String filePath) {
    withCredentials([file(credentialsId: 'gpg-key', variable: 'GPGKEY')]) {
        sh 'gpg --batch --yes --import "$GPGKEY"'
        sh "gpg --batch --yes --pinentry-mode loopback --passphrase \"\$GPG_PASSPHRASE\" -ab ${filePath}"
    }
    echo "[deployHelpers] Signed ${filePath}.asc"
}

/** Sign a container image with cosign. Expects a 'cosign-key' file credential. */
def signWithCosign(String imageTag) {
    withCredentials([file(credentialsId: 'cosign-key', variable: 'COSIGN_KEY')]) {
        sh "cosign sign --key \$COSIGN_KEY ${imageTag}"
    }
    echo "[deployHelpers] cosign-signed ${imageTag}"
}

/** Push Maven build artifacts + build-info to Artifactory. */
def pushMavenArtifacts(Map config) {
    def server = Artifactory.server(config.serverId ?: 'jfrog-server-id')
    def rtMaven = Artifactory.newMavenBuild()
    rtMaven.tool = config.mavenTool ?: 'maven-3.8.1'
    rtMaven.deployer releaseRepo: config.releaseRepo ?: 'libs-release-local',
                      snapshotRepo: config.snapshotRepo ?: 'libs-snapshots-local',
                      server: server
    rtMaven.deployer.deployArtifacts = true
    rtMaven.run pom: config.pom ?: 'pom.xml', goals: 'clean deploy'
    rtMaven.deployer.deployBuildInfo = true
    echo "[deployHelpers] Maven artifacts pushed to Artifactory"
}

/** Push a Docker image to the Artifactory Docker registry, tagged with build metadata. */
def pushDockerImage(String imageTag, Map config) {
    def dockerRepo = config.dockerRepo ?: 'docker-local'
    def buildName = config.buildName ?: env.JOB_NAME
    def buildNumber = config.buildNumber ?: env.BUILD_NUMBER
    sh "jfrog rt docker-push ${imageTag} ${dockerRepo} --build-name=${buildName} --build-number=${buildNumber}"
    echo "[deployHelpers] Pushed ${imageTag} to ${dockerRepo}"
}

/**
 * GitOps promotion: update the image tag / Helm value in the config repo folder for the target
 * environment and let Argo CD pick it up (do NOT rebuild). This assumes the config repo is
 * already checked out at configRepoDir.
 */
def promoteViaGitOps(Map config) {
    def env_ = config.environment            // dev | staging | prod
    def configRepoDir = config.configRepoDir
    def imageTag = config.imageTag
    def valuesFile = "${configRepoDir}/${env_}/values.yaml"

    dir(configRepoDir) {
        sh """
            yq -i '.image.tag = "${imageTag}"' ${valuesFile}
            git config user.email 'ci-bot@company.com'
            git config user.name 'ci-bot'
            git add ${valuesFile}
            git commit -m 'Promote myapp to ${env_}: ${imageTag}' || echo 'No changes to commit'
            git push origin HEAD:main
        """
    }
    echo "[deployHelpers] Promoted ${imageTag} to ${env_} via GitOps commit"
}

/** Trigger (or wait for) an Argo CD sync for the given application. */
def syncArgoApp(String appName, Map config = [:]) {
    def prune = config.prune != false ? '--prune' : ''
    def waitHealthy = config.wait != false
    sh "argocd app sync ${appName} ${prune}"
    if (waitHealthy) {
        sh "argocd app wait ${appName} --health --timeout ${config.timeoutSeconds ?: 300}"
    }
    echo "[deployHelpers] Argo CD synced ${appName}"
}

/** Roll an Argo CD application back to a previous history ID (or via Git revert if omitted). */
def rollbackArgoApp(String appName, String historyId = null) {
    if (historyId) {
        sh "argocd app rollback ${appName} ${historyId}"
        echo "[deployHelpers] Rolled back ${appName} to history ${historyId}"
    } else {
        error "[deployHelpers] No historyId given — perform a 'git revert' on the config repo instead and let Argo CD auto-sync."
    }
}

return this
