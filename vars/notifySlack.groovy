// vars/notifySlack.groovy
// Global step: notifySlack(color: 'good'|'warning'|'danger', message: '...')
// Requires the Jenkins Slack Notification plugin configured with a 'slack-webhook' credential
// and a default workspace/team under Manage Jenkins -> System -> Slack.

def call(Map config) {
    def color = config.color ?: 'warning'
    def message = config.message ?: 'Jenkins notification'
    def channel = config.channel ?: '#ci-cd-alerts'

    slackSend(
        channel: channel,
        color: color,
        message: message,
        tokenCredentialId: 'slack-webhook'
    )
}
