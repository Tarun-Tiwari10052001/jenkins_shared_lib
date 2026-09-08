package org.company.pipeline

/**
 * Small notification helper kept out of vars/ so it's independently unit
 * testable (Groovy classes under src/ are plain, non-CPS-transformed code).
 */
class Notifier implements Serializable {

    private final def steps

    Notifier(steps) {
        this.steps = steps
    }

    void slack(String channel, String message, String color = '#439FE0') {
        try {
            steps.slackSend(channel: channel, color: color, message: message)
        } catch (ignored) {
            steps.echo "Slack notification unavailable/skipped: ${message}"
        }
    }
}
