// vars/resolvePromotionSourceTag.groovy
//
// For release/* and master builds, resolves WHICH already-built image tag
// should be promoted, implementing build-once/promote-many:
//   1. Explicit override via the PROMOTE_FROM_TAG build parameter, if set.
//   2. Otherwise, auto-resolve the most recently published "develop-*" tag
//      from Artifactory.
def call(String explicitTag) {
    if (explicitTag?.trim()) {
        echo "Using explicit PROMOTE_FROM_TAG override: ${explicitTag}"
        return explicitTag.trim()
    }

    def resolved = sh(
        script: """
            jf rt search "docker-local/${env.APP_NAME}/develop-*/manifest.json" \
                --url ${env.ARTIFACTORY_URL} --sort-by=created --sort-order=desc --limit=1 \
                | jq -r '.[0].path' | awk -F'/' '{print \$(NF-1)}'
        """,
        returnStdout: true
    ).trim()

    if (!resolved) {
        error 'Could not auto-resolve a develop-* build to promote. Re-run with PROMOTE_FROM_TAG set explicitly.'
    }
    echo "Auto-resolved promotion source: ${resolved}"
    return resolved
}
