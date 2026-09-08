// vars/dastScan.groovy
//
// Dynamic Application Security Testing against a live, deployed environment
// using OWASP ZAP baseline scan.
def call(String targetUrl) {
    stage('DAST Scan - OWASP ZAP') {
        sh """
            docker run --rm -v \$(pwd):/zap/wrk/:rw owasp/zap2docker-stable \
                zap-baseline.py -t ${targetUrl} -r zap-report.html -I
        """
        archiveArtifacts artifacts: 'zap-report.html', allowEmptyArchive: true
    }
}
