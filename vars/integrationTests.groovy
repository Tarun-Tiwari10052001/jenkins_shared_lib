// vars/integrationTests.groovy
//
// Runs the Postman/Newman API + integration test collection against a live
// deployed environment.
def call(String targetUrl) {
    stage('Integration & API Tests') {
        sh """
            newman run tests/postman/api-collection.json \
                --env-var baseUrl=${targetUrl} \
                --reporters cli,junit --reporter-junit-export newman-report.xml
        """
        junit testResults: 'newman-report.xml', allowEmptyResults: true
    }
}
