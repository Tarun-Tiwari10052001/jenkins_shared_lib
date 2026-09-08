package org.company.pipeline

/**
 * Small stateless helpers shared across pipeline stages.
 * Import in a Jenkinsfile via the Shared Library classloader, e.g.:
 *   import org.company.pipeline.Utility
 *   Utility.branchType(env.BRANCH_NAME)
 */
class Utility implements Serializable {

    /** Classify a branch name into one of the GitFlow roles. */
    static String branchType(String branchName) {
        if (branchName == null) return 'unknown'
        if (branchName == 'main' || branchName == 'master') return 'main'
        if (branchName == 'develop' || branchName.startsWith('dev')) return 'develop'
        if (branchName.startsWith('release/')) return 'release'
        if (branchName.startsWith('hotfix/')) return 'hotfix'
        if (branchName.startsWith('feature/')) return 'feature'
        return 'other'
    }

    /** True if this branch should be treated as protected / release-grade. */
    static boolean isProtectedBranch(String branchName) {
        def type = branchType(branchName)
        return type == 'main' || type == 'release' || type == 'hotfix'
    }

    /** True if artifacts built from this branch should be pushed as final (non-SNAPSHOT). */
    static boolean isReleaseArtifact(String branchName) {
        def type = branchType(branchName)
        return type == 'main' || type == 'hotfix'
    }

    /** Shorten a full git SHA to the conventional 7-character form, defensively. */
    static String shortCommit(String fullSha) {
        if (!fullSha) return 'unknown'
        return fullSha.length() > 7 ? fullSha.substring(0, 7) : fullSha
    }

    /** Sanitize a branch name for use inside a Docker tag (no slashes, lowercase). */
    static String sanitizeForTag(String branchName) {
        if (!branchName) return 'unknown'
        return branchName.toLowerCase().replaceAll('[^a-z0-9._-]', '-')
    }

    /** Resolve which Argo CD application a branch should deploy to on success, or null. */
    static String targetArgoApp(String branchName, Map argoApps) {
        def type = branchType(branchName)
        switch (type) {
            case 'feature':
            case 'develop':
                return argoApps.dev
            case 'release':
                return argoApps.staging
            case 'main':
            case 'hotfix':
                return argoApps.prod
            default:
                return null
        }
    }
}
