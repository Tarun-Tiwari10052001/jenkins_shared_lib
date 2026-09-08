// vars/dependencyCheck.groovy
//
// Software Composition Analysis (SCA) for third-party libraries:
//   - OWASP Dependency-Check (Maven plugin) for known-CVE dependency findings
//   - Trivy filesystem scan as a complementary, fast-moving vulnerability/
//     license feed over the dependency tree
def call() {
    stage('Dependency & SCA Scanning') {
        sh '''
            mvn -B org.owasp:dependency-check-maven:check \
                -DfailBuildOnCVSS=8 \
                -Dformat=ALL \
                -DsuppressionFile=.dependency-check-suppressions.xml || true
        '''
        dependencyCheckPublisher(
            pattern: 'target/dependency-check-report.xml',
            failedTotalCritical: 0,
            unstableTotalHigh: 1
        )

        sh '''
            trivy fs --scanners vuln,license \
                --severity HIGH,CRITICAL \
                --exit-code 0 \
                --format json -o trivy-fs-report.json .
        '''

        archiveArtifacts artifacts: 'trivy-fs-report.json, target/dependency-check-report.*', allowEmptyArchive: true
    }
}
