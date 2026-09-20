// as running for local other wise it risky to expose sonar token: jenkins-myapp : sqp_a076a5480a4b84b86bded8b18934bcd4fb315637
// vars/sonarAnalysis.groovy
//
// Static code analysis via SonarQube, gated by the SonarQube Quality Gate
// (webhook-driven, non-polling).
def call() {
    stage('Static Code Analysis - SonarQube') {
        withVaultSecrets(secrets: [[envVar: 'SONAR_TOKEN', vaultKey: 'sonar_token']]) {
            withSonarQubeEnv('company-sonarqube') {
                sh """
                    mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                        -Dsonar.projectKey=${env.APP_NAME} \
                        -Dsonar.branch.name=${env.BRANCH_NAME} \
                        -Dsonar.login=\$SONAR_TOKEN
                """
            }
        }

        timeout(time: 15, unit: 'MINUTES') {
            def qg = waitForQualityGate()
            if (qg.status != 'OK') {
                error "SonarQube Quality Gate failed: ${qg.status}"
            }
            echo 'SonarQube Quality Gate passed.'
        }
    }
}
