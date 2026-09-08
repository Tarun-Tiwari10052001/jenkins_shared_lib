// vars/resolveExistingImage.groovy
//
// Implements the "promote" half of build-once/promote-many: pulls the image
// that was already built (and scanned/signed) on develop or a hotfix branch,
// and retags it for the current release/master build WITHOUT recompiling or
// rebuilding anything.
def call(String sourceTag) {
    stage('Resolve Existing Build Artifact') {
        sh """
            docker pull ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${sourceTag}
            docker tag ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${sourceTag} \
                ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.IMAGE_TAG}
        """
        echo "Promoting ${sourceTag} -> ${env.IMAGE_TAG} (no rebuild)."
    }
}
