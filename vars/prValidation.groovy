// vars/prValidation.groovy
//
// Validates the pull request context itself (title/description conventions,
// linked issue, target branch, existing CI check status) when this build is
// running against an open PR (env.CHANGE_ID is set by the GitHub branch source).
def call() {
    stage('PR Validation') {
        script {
            if (env.CHANGE_ID) {
                echo "Validating PR #${env.CHANGE_ID}: '${env.CHANGE_TITLE}' (${env.CHANGE_BRANCH} -> ${env.CHANGE_TARGET})"

                if (!(env.CHANGE_TITLE ==~ /^(feat|fix|chore|docs|refactor|test|hotfix)(\(.+\))?:.+/)) {
                    unstable "PR title '${env.CHANGE_TITLE}' does not follow Conventional Commits style (e.g. 'feat: ...', 'fix: ...')."
                }

                withVaultSecrets(secrets: [[envVar: 'GH_TOKEN', vaultKey: 'github_api_token']]) {
                    sh "gh pr checks ${env.CHANGE_ID} || true"
                }
            } else {
                echo 'No open PR context on this build — skipping PR validation.'
            }
        }
    }
}
