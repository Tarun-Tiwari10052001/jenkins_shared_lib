// vars/trivyImageScan.groovy
//
// Container image vulnerability scan. Runs both right after the image is
// built AND again (on the retagged image) during release/master promotion,
// so every environment gets a fresh compliance check without rebuilding.
def call(String tag) {
    stage('Container Image Scan - Trivy') {
        sh """
            trivy image \
                --severity HIGH,CRITICAL \
                --exit-code 1 \
                --format json -o trivy-image-${tag}.json \
                ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${tag}
        """
        archiveArtifacts artifacts: "trivy-image-${tag}.json", allowEmptyArchive: true
    }
}
