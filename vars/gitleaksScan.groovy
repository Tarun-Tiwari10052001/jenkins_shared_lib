// vars/gitleaksScan.groovy
//
// Secrets detection across the full Git history of the working tree.
// Gitleaks exits non-zero on any finding, which fails the `sh` step and the
// build — secrets in source are treated as a hard stop, not a warning.
def call() {
    stage('Secrets Scan - Gitleaks') {
        sh '''
            gitleaks detect \
                --source . \
                --report-format json \
                --report-path gitleaks-report.json \
                --redact \
                --exit-code 1
        '''
        archiveArtifacts artifacts: 'gitleaks-report.json', allowEmptyArchive: true
    }
}
