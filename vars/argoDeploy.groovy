// vars/argoDeploy.groovy
//
// Deploys the already-built, already-signed image to a target environment
// via Argo CD (GitOps): update the app's image tag, sync, and wait for
// health, then emit a monitoring annotation for that deployment.
def call(String environment, String tag) {
    stage("Deploy to ${environment.toUpperCase()} - Argo CD") {
        withVaultSecrets(secrets: [[envVar: 'ARGOCD_AUTH_TOKEN', vaultKey: 'argocd_token']]) {
            sh """
                argocd app set ${env.APP_NAME}-${environment} \
                    --helm-set image.tag=${tag} \
                    --auth-token \$ARGOCD_AUTH_TOKEN --server ${env.ARGOCD_SERVER}

                argocd app sync ${env.APP_NAME}-${environment} \
                    --auth-token \$ARGOCD_AUTH_TOKEN --server ${env.ARGOCD_SERVER} --prune

                argocd app wait ${env.APP_NAME}-${environment} \
                    --auth-token \$ARGOCD_AUTH_TOKEN --server ${env.ARGOCD_SERVER} \
                    --health --timeout 300
            """
        }
        monitoringAnnotate(environment, tag)
    }
}
