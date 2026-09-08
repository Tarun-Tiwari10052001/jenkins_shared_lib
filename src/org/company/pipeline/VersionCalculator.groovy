package org.company.pipeline

/**
 * Computes Maven version + Docker tag + (optional) Git tag for a build,
 * based on GitFlow branch type. Mirrors docs/TAGGING_TRIGGERS.md.
 */
class VersionCalculator implements Serializable {

    /**
     * @param branchName   env.BRANCH_NAME
     * @param baseVersion  current version from pom.xml / package.json, e.g. "1.2.3" or "1.2.3-SNAPSHOT"
     * @param buildNumber  env.BUILD_NUMBER
     * @param shortSha     7-char git commit
     * @return Map with keys: mavenVersion, dockerTag, gitTag (nullable), isRelease (bool)
     */
    static Map compute(String branchName, String baseVersion, String buildNumber, String shortSha) {
        def type = Utility.branchType(branchName)
        def core = baseVersion?.replace('-SNAPSHOT', '') ?: '0.0.0'
        def safeBranch = Utility.sanitizeForTag(branchName)

        switch (type) {
            case 'feature':
                return [
                    mavenVersion: "${core}-SNAPSHOT",
                    dockerTag   : "${safeBranch}-${shortSha}",
                    gitTag      : null,
                    isRelease   : false
                ]
            case 'develop':
                return [
                    mavenVersion: "${core}-SNAPSHOT",
                    dockerTag   : "dev-${shortSha}",
                    gitTag      : null,
                    isRelease   : false
                ]
            case 'release':
                // e.g. release/1.2.3 -> 1.2.3-RC1
                def rcNum = buildNumber ?: '1'
                return [
                    mavenVersion: "${core}-RC${rcNum}",
                    dockerTag   : "${core}-rc${rcNum}",
                    gitTag      : "v${core}-rc${rcNum}",
                    isRelease   : false
                ]
            case 'main':
                return [
                    mavenVersion: core,
                    dockerTag   : core,
                    gitTag      : "v${core}",
                    isRelease   : true
                ]
            case 'hotfix':
                return [
                    mavenVersion: core,
                    dockerTag   : core,
                    gitTag      : "v${core}",
                    isRelease   : true
                ]
            default:
                return [
                    mavenVersion: "${core}-SNAPSHOT",
                    dockerTag   : "${safeBranch}-${shortSha}",
                    gitTag      : null,
                    isRelease   : false
                ]
        }
    }
}
