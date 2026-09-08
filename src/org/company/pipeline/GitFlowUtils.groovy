package org.company.pipeline

/**
 * Pure branch-name classification helpers for GitFlow, reused by both the
 * Jenkinsfile (if needed) and library vars/ steps that need branch-type
 * decisions beyond what a declarative `when { branch ... }` can express.
 */
class GitFlowUtils implements Serializable {

    static boolean isFeature(String branch) { branch?.startsWith('feature/') }

    static boolean isHotfix(String branch) { branch?.startsWith('hotfix/') }

    static boolean isRelease(String branch) { branch?.startsWith('release/') }

    static boolean isDevelop(String branch) { branch == 'develop' }

    static boolean isMaster(String branch) { branch == 'master' }

    /** Branches that build fresh from source rather than promoting an existing image. */
    static boolean isFreshBuildBranch(String branch) {
        isFeature(branch) || isHotfix(branch) || isDevelop(branch)
    }

    /** Branches that promote an already-built image rather than rebuilding. */
    static boolean isPromotionBranch(String branch) {
        isRelease(branch) || isMaster(branch)
    }
}
