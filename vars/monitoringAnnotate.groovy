// vars/monitoringAnnotate.groovy
//
// Annotates Grafana with a deployment event (visible on dashboards backed by
// Prometheus metrics and Loki logs), and tags the deployment with the
// app/env/version labels those systems key off of for correlation.
def call(String environment, String tag) {
    stage("Monitoring Annotation - ${environment}") {
        withVaultSecrets(secrets: [[envVar: 'GRAFANA_API_KEY', vaultKey: 'grafana_api_key']]) {
            sh """
                curl -s -X POST ${env.GRAFANA_URL}/api/annotations \
                    -H "Authorization: Bearer \$GRAFANA_API_KEY" \
                    -H 'Content-Type: application/json' \
                    -d '{"text":"Deployed ${env.APP_NAME}:${tag} to ${environment}","tags":["deployment","${environment}","${env.APP_NAME}"]}'
            """
        }
        echo "Prometheus/Loki labels app=${env.APP_NAME}, env=${environment}, version=${tag} " +
             "are applied via the Argo CD Helm values (see resources/argocd-app-template.yaml) " +
             "so runtime metrics and logs for this release are queryable immediately."
    }
}
