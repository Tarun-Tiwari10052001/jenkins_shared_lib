// vars/dockerBuildSbom.groovy
//
// Builds the container image exactly once for this commit/tag and generates
// a CycloneDX SBOM for it (syft). This image tag is the artifact that will be
// signed, pushed, and later PROMOTED (not rebuilt) through Dev -> QA -> Prod.
def call(String tag) {
    stage('Docker Build & SBOM') {
        sh "docker build -t ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${tag} ."

        sh """
            syft ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${tag} \
                -o cyclonedx-json > sbom-${tag}.json
        """

        archiveArtifacts artifacts: "sbom-${tag}.json", fingerprint: true
    }
}
