// vars/preCommitChecks.groovy
//
// Fast, local-feeling checks that run before anything expensive: formatting,
// linting, and basic style/convention enforcement.
def call() {
    stage('Pre-Commit Checks') {
        sh '''
            pip install --quiet --user pre-commit || true
            pre-commit run --all-files --show-diff-on-failure || true
        '''
        sh 'mvn -B -q checkstyle:check'
    }
}
