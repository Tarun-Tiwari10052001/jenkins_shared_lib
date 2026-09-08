// vars/configureBranchTriggers.groovy
//
// Applies GitFlow-aware, branch-specific triggers via `properties()`.
// Must be called at SCRIPT scope, before the pipeline{} block, since
// declarative `triggers{}` cannot branch on env.BRANCH_NAME.
//
//   feature/*  -> GitHub webhook (Generic Webhook Trigger plugin) on push
//   hotfix/*   -> GitHub webhook (Generic Webhook Trigger plugin) on push
//   develop    -> nightly cron build
//   release/*  -> no automatic trigger (manual build or Git-tag build only)
//   master     -> no automatic trigger (manual build or Git-tag build only)
def call(String branchName) {
    if (!branchName) {
        echo 'configureBranchTriggers: env.BRANCH_NAME not available (not a Multibranch job?) — skipping.'
        return
    }

    if (branchName ==~ /^(feature|hotfix)\/.*/) {
        def tokenSafeBranch = branchName.replaceAll('/', '-')
        properties([
            pipelineTriggers([
                GenericTrigger(
                    genericVariables: [
                        [key: 'ref',        value: '$.ref'],
                        [key: 'pusher',     value: '$.pusher.name'],
                        [key: 'repository', value: '$.repository.full_name']
                    ],
                    causeString: "Triggered by GitHub push to ${branchName} (pusher: \$pusher)",
                    token: "github-${tokenSafeBranch}",
                    tokenCredentialId: '',
                    printContributedVariables: false,
                    printPostContent: false,
                    silentResponse: false,
                    regexpFilterText: '$ref',
                    regexpFilterExpression: "refs/heads/${branchName}\$"
                )
            ])
        ])
        echo "Configured GitHub GenericTrigger webhook for '${branchName}' (token: github-${tokenSafeBranch})."

    } else if (branchName == 'develop') {
        properties([
            pipelineTriggers([
                cron('H 22 * * 1-5') // nightly, weeknights, Jenkins-hashed minute
            ])
        ])
        echo "Configured nightly cron trigger for 'develop'."

    } else {
        // master and release/* — no automatic trigger.
        // Builds happen manually, or via "Discover tags" on an annotated
        // Git tag push (configured on the Multibranch Pipeline job itself).
        properties([
            pipelineTriggers([])
        ])
        echo "No automatic trigger configured for '${branchName}' — manual or Git-tag build only."
    }
}
