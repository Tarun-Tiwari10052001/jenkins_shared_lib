// vars/verifyArtifact.groovy
//
// Verifies the cosign signature on the image and confirms the Maven
// artifact is present in Artifactory BEFORE production deploy — this is the
// "reuse" half of build-once/promote-many: we verify identity, we do not
// rebuild.
def call(String tag) {
    stage('Verify Artifact/Image') {
        sh """
            cosign verify --key cosign.pub ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${tag}
        """
        withVaultSecrets(secrets: [
            [envVar: 'ARTIFACTORY_USER', vaultKey: 'artifactory_user'],
            [envVar: 'ARTIFACTORY_PASS', vaultKey: 'artifactory_pass']
        ]) {
            sh """
                jf rt search "libs-release-local/${env.APP_NAME}/${tag}/*.jar" \
                    --url ${env.ARTIFACTORY_URL} --user "\$ARTIFACTORY_USER" --password "\$ARTIFACTORY_PASS"
            """
        }
        echo "Verified signature + Artifactory presence for ${env.APP_NAME}:${tag}."
    }
}
