// vars/notifyTeam.groovy
//
// Thin wrapper so Jenkinsfiles never call notification plugins directly.
import org.company.pipeline.Notifier

def call(String message, String color = '#439FE0') {
    new Notifier(this).slack('#ci-cd-alerts', message, color)
}
