// vars/codeReviewGate.groovy
//
// Enforces a minimum number of human approvals on the associated PR before
// allowing the pipeline to proceed into build/scan/deploy stages.
def call(int minApprovals = 1) {
    stage('Code Review Gate') {
        script {
            if (!env.CHANGE_ID) {
                echo 'Not a PR build — skipping code review gate.'
                return
            }

            withVaultSecrets(secrets: [[envVar: 'GH_TOKEN', vaultKey: 'github_api_token']]) {
                def approvals = sh(
                    script: """
                        curl -s -H "Authorization: token \$GH_TOKEN" \
                            "https://api.github.com/repos/company/${env.APP_NAME}/pulls/${env.CHANGE_ID}/reviews" \
                        | jq '[.[] | select(.state=="APPROVED")] | length'
                    """,
                    returnStdout: true
                ).trim()

                if (approvals.toInteger() < minApprovals) {
                    error "Code review gate failed: PR #${env.CHANGE_ID} has ${approvals} approval(s), needs ${minApprovals}."
                }
                echo "Code review gate passed: ${approvals} approval(s) on PR #${env.CHANGE_ID}."
            }
        }
    }
}
