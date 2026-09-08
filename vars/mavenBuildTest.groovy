// vars/mavenBuildTest.groovy
//
// Compiles, unit-tests (JUnit) and packages the application with Maven.
def call() {
    stage('Build & Unit Test') {
        sh 'mvn -B clean package'
        junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true

        script {
            if (fileExists('target/jacoco.exec')) {
                jacoco execPattern: 'target/jacoco.exec'
            }
        }

        archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
    }
}
