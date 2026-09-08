// vars/semVer.groovy
//
// Resolves a semantic version string for the current build:
//   - release/*  or tag build -> version derived from the branch/tag name
//                                (e.g. release/1.2.3 or tag v1.2.3 -> 1.2.3)
//   - otherwise                -> the Maven pom.xml <version> with any
//                                "-SNAPSHOT" suffix stripped
def call() {
    if (env.TAG_NAME) {
        return env.TAG_NAME.replaceFirst('^v', '')
    }
    if (env.BRANCH_NAME ==~ /^release\/.*/) {
        return env.BRANCH_NAME.replaceFirst('^release/', '').replaceFirst('^v', '')
    }
    def pomVersion = sh(
        script: "mvn -q -Dexec.executable=echo -Dexec.args='\${project.version}' --non-recursive org.codehaus.mojo:exec-maven-plugin:3.1.0:exec",
        returnStdout: true
    ).trim()
    return pomVersion.replace('-SNAPSHOT', '')
}
