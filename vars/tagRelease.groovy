// vars/tagRelease.groovy
//
// Creates and pushes an annotated Git tag for a finalized release, enforcing
// semantic versioning on release/* branches.
def call(String imageTag) {
    stage('Tag Release in Git') {
        script {
            def version = imageTag.replaceFirst('^release-v', '')
            withVaultSecrets(secrets: [[envVar: 'GIT_PUSH_TOKEN', vaultKey: 'github_push_token']]) {
                sh """
                    git config user.email 'ci-bot@company.com'
                    git config user.name 'ci-bot'
                    git tag -a v${version} -m 'Release v${version} (build ${env.BUILD_NUMBER})'
                    git push https://x-access-token:\$GIT_PUSH_TOKEN@github.com/company/${env.APP_NAME}.git v${version}
                """
            }
            echo "Pushed Git tag v${version} for this release."
        }
    }
}
