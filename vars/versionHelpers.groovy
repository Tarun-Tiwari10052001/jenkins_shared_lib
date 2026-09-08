// vars/versionHelpers.groovy
// Global step: versionHelpers.xxx(...)
import org.company.pipeline.VersionCalculator
import org.company.pipeline.Utility

/**
 * Compute { mavenVersion, dockerTag, gitTag, isRelease } for the current branch/build.
 * Reads the base version out of pom.xml unless one is passed explicitly.
 */
def resolve(Map config = [:]) {
    def branchName = config.branchName ?: env.BRANCH_NAME
    def buildNumber = config.buildNumber ?: env.BUILD_NUMBER
    def shortSha = Utility.shortCommit(config.gitCommit ?: env.GIT_COMMIT)
    def baseVersion = config.baseVersion ?: readMavenPomVersion(config.pom ?: 'pom.xml')

    def result = VersionCalculator.compute(branchName, baseVersion, buildNumber, shortSha)
    echo "[versionHelpers] branch=${branchName} -> maven=${result.mavenVersion}, docker=${result.dockerTag}, gitTag=${result.gitTag}"
    return result
}

/** Extract <version> from a pom.xml without a full Maven invocation. */
def readMavenPomVersion(String pomPath) {
    def pom = readFile(pomPath)
    def matcher = pom =~ /<version>(.*?)<\/version>/
    return matcher ? matcher[0][1] : '0.0.0-SNAPSHOT'
}

/** Create and push an annotated Git tag if the branch type calls for one. */
def tagRelease(String gitTag, String message = null) {
    if (!gitTag) {
        echo "[versionHelpers] No git tag required for this branch"
        return
    }
    sh """
        git tag -a ${gitTag} -m "${message ?: "Release ${gitTag}"}"
        git push origin ${gitTag}
    """
    echo "[versionHelpers] Pushed tag ${gitTag}"
}

return this
