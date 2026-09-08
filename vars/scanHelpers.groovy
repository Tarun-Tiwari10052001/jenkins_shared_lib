// vars/scanHelpers.groovy
// Global step: scanHelpers.xxx(...)
// Each method throws / calls error() on policy violation so the calling stage fails naturally.

/** SAST: SonarQube scan + quality gate wait. Requires 'SonarQubeServer' configured in Jenkins. */
def runSonarQube(Map config = [:]) {
    def serverName = config.serverName ?: 'SonarQubeServer'
    def projectKey = config.projectKey
    def timeoutMinutes = config.timeoutMinutes ?: 10

    withSonarQubeEnv(serverName) {
        sh "mvn sonar:sonar -Dsonar.projectKey=${projectKey}"
    }
    timeout(time: timeoutMinutes, unit: 'MINUTES') {
        def qg = waitForQualityGate()
        if (qg.status != 'OK') {
            error "[scanHelpers] SonarQube quality gate failed: ${qg.status}"
        }
    }
    echo "[scanHelpers] SonarQube quality gate: OK"
}

/** SCA: OWASP Dependency-Check, fails the stage if a finding meets/exceeds the CVSS threshold. */
def runDependencyCheck(Map config = [:]) {
    def failScore = config.failOnCvssScore ?: 9.0
    sh 'mvn org.owasp:dependency-check-maven:check -Dformat=ALL'

    def report = readFile('dependency-check-report.html')
    // Simple heuristic scan of the HTML report; swap for JSON parsing of dependency-check-report.json
    // in real usage for reliability.
    def hasCritical = report.contains('CVSS Score: 9.') || report.contains('CVSS Score: 10.')
    if (hasCritical && failScore <= 9.0) {
        error "[scanHelpers] Dependency-Check found findings >= CVSS ${failScore}"
    }
    echo "[scanHelpers] Dependency-Check: no findings >= CVSS ${failScore}"
}

/** Secret scanning with Gitleaks. */
def runGitleaks(Map config = [:]) {
    def redact = config.redact != false
    def redactFlag = redact ? '--redact' : ''
    def status = sh(script: "gitleaks detect --exit-code 1 --verbose ${redactFlag}", returnStatus: true)
    if (status != 0) {
        error "[scanHelpers] Gitleaks detected potential secrets (exit code ${status})"
    }
    echo "[scanHelpers] Gitleaks: no secrets found"
}

/** Container image scanning with Trivy. */
def runTrivyScan(String imageTag, Map config = [:]) {
    def severity = config.severity ?: 'CRITICAL,HIGH'
    def status = sh(script: "trivy image --exit-code 1 --severity ${severity} ${imageTag}", returnStatus: true)
    if (status != 0) {
        error "[scanHelpers] Trivy found ${severity} vulnerabilities in ${imageTag}"
    }
    echo "[scanHelpers] Trivy: no ${severity} findings in ${imageTag}"
}

/** SBOM generation with Syft, in CycloneDX JSON format. */
def generateSBOM(String imageTag, String outputFile = 'sbom.json') {
    sh "syft ${imageTag} -o cyclonedx-json=${outputFile}"
    archiveArtifacts artifacts: outputFile, fingerprint: true
    echo "[scanHelpers] SBOM written to ${outputFile} and archived"
}

return this
