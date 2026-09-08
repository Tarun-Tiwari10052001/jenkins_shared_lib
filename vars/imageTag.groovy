// vars/imageTag.groovy
//
// GitFlow-aware tagging convention (branch/environment + version):
//   develop     -> myapp:develop-<build number>
//   feature/*   -> myapp:feature-<slug>-<build number>
//   hotfix/*    -> myapp:hotfix-<slug>-<build number>
//   release/*   -> myapp:release-v<semver>
//   master/tag  -> myapp:v<semver>
def call() {
    def branch = env.BRANCH_NAME ?: 'unknown'

    if (branch == 'develop') {
        return "develop-${env.BUILD_NUMBER}"
    }
    if (branch ==~ /^feature\/.*/) {
        def slug = branch.replaceFirst('^feature/', '').replaceAll('[^a-zA-Z0-9._-]', '-').toLowerCase()
        return "feature-${slug}-${env.BUILD_NUMBER}"
    }
    if (branch ==~ /^hotfix\/.*/) {
        def slug = branch.replaceFirst('^hotfix/', '').replaceAll('[^a-zA-Z0-9._-]', '-').toLowerCase()
        return "hotfix-${slug}-${env.BUILD_NUMBER}"
    }
    if (branch ==~ /^release\/.*/ || env.TAG_NAME) {
        return "release-v${semVer()}"
    }
    if (branch == 'master') {
        return "v${semVer()}"
    }
    return "${branch.replaceAll('[^a-zA-Z0-9._-]', '-')}-${env.BUILD_NUMBER}"
}
