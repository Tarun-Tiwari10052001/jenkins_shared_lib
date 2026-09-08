// vars/securityComplianceGate.groovy
//
// The final gate before Production: an automated check (no CRITICAL
// vulnerabilities open on the promoted image) combined with a mandatory
// manual approval from Release Management / Security.
def call() {
    stage('Security & Compliance Gate') {
        script {
            def reportFile = "trivy-image-${env.IMAGE_TAG}.json"
            if (fileExists(reportFile)) {
                def report = readJSON file: reportFile
                int criticalCount = 0
                (report.Results ?: []).each { r ->
                    (r.Vulnerabilities ?: []).each { v -> if (v.Severity == 'CRITICAL') criticalCount++ }
                }
                if (criticalCount > 0) {
                    error "Compliance gate failed: ${criticalCount} CRITICAL vulnerabilit(y/ies) open in ${env.IMAGE_TAG}."
                }
                echo 'Automated compliance check passed: no open CRITICAL vulnerabilities.'
            } else {
                echo "No Trivy report found for ${env.IMAGE_TAG} at this stage — relying on manual approval only."
            }
        }

        timeout(time: 30, unit: 'MINUTES') {
            input message: "Approve promotion of ${env.APP_NAME}:${env.IMAGE_TAG} to Production?",
                  ok: 'Approve',
                  submitter: 'release-managers,security-team'
        }
    }
}
