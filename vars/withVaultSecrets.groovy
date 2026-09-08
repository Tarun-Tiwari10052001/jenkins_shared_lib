// vars/withVaultSecrets.groovy
//
// Thin wrapper around the HashiCorp Vault plugin's `withVault` step so every
// other library step can fetch exactly the secrets it needs, scoped tightly
// to its own block (least privilege), without hard-coding secret material or
// Vault paths in the Jenkinsfile.
//
// Usage:
//   withVaultSecrets(secrets: [[envVar: 'SONAR_TOKEN', vaultKey: 'sonar_token']]) {
//       sh 'echo $SONAR_TOKEN | ...'
//   }
def call(Map config, Closure body) {
    def path = config.path ?: "secret/data/${env.APP_NAME ?: 'myapp'}"
    def secretValues = config.secrets.collect { s -> [envVar: s.envVar, vaultKey: s.vaultKey] }

    withVault(
        vaultSecrets: [[path: path, secretValues: secretValues]],
        vaultUrl: env.VAULT_ADDR ?: 'https://vault.company.com:8200',
        vaultCredentialId: 'vault-approle',
        options: [timeout: 60]
    ) {
        body()
    }
}
