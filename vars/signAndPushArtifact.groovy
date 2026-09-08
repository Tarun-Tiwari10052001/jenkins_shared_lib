// vars/signAndPushArtifact.groovy
//
// Signs the container image (cosign) and pushes both the image and the
// Maven artifact to JFrog Artifactory, tagged per the GitFlow convention.
def call(String tag) {
    stage('Sign & Push to Artifactory') {
        withVaultSecrets(secrets: [
            [envVar: 'ARTIFACTORY_USER',     vaultKey: 'artifactory_user'],
            [envVar: 'ARTIFACTORY_PASS',     vaultKey: 'artifactory_pass'],
            [envVar: 'COSIGN_KEY_PASSWORD',  vaultKey: 'cosign_password']
        ]) {
            sh """
                echo "\$ARTIFACTORY_PASS" | docker login ${env.DOCKER_REGISTRY} -u "\$ARTIFACTORY_USER" --password-stdin
                docker push ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${tag}

                cosign sign --yes \
                    --key vault://transit/cosign-signing-key \
                    ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${tag}
            """

            script {
                if (fileExists("${env.WORKSPACE}/target")) {
                    sh """
                        jf rt upload "target/*.jar" \
                            "libs-release-local/${env.APP_NAME}/${tag}/" \
                            --url ${env.ARTIFACTORY_URL} \
                            --user "\$ARTIFACTORY_USER" --password "\$ARTIFACTORY_PASS" \
                            --build-name ${env.APP_NAME} --build-number ${env.BUILD_NUMBER}
                    """
                }
            }
        }
        echo "Pushed & signed ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${tag}"
    }
}
