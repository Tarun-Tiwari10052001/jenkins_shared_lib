// vars/checkoutCode.groovy
//
// Standard SCM checkout + commit metadata capture, shared across all branches.
def call() {
    stage('Checkout') {
        checkout scm

        script {
            env.GIT_COMMIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
            env.GIT_AUTHOR = sh(script: "git log -1 --pretty=format:'%an'", returnStdout: true).trim()
            currentBuild.description = "${env.BRANCH_NAME} @ ${env.GIT_COMMIT_SHORT} by ${env.GIT_AUTHOR}"
            echo "Checked out ${env.BRANCH_NAME} @ ${env.GIT_COMMIT_SHORT}"
        }
    }
}
