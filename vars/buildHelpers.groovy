// vars/buildHelpers.groovy
// Global step: buildHelpers.xxx(...)

/** Build + unit-test a Maven-based Java app, publishing surefire results. */
def buildJavaApp(Map config) {
    def pom = config.pom ?: 'pom.xml'
    def mvnArgs = config.mvnArgs ?: '-DskipTests=false'
    echo "[buildHelpers] Building ${pom} with args: ${mvnArgs}"
    sh "mvn -f ${pom} clean verify ${mvnArgs}"
}

/** Build a Docker image and return the fully-qualified tag used. */
def buildDockerImage(Map config) {
    def registry = config.registry
    def imageName = config.imageName
    def tag = config.tag
    def dockerfile = config.dockerfile ?: 'Dockerfile'
    def context = config.context ?: '.'
    def fqTag = "${registry}/${imageName}:${tag}"

    echo "[buildHelpers] Building image ${fqTag}"
    sh "docker build -f ${dockerfile} -t ${fqTag} ${context}"
    return fqTag
}

/** Additionally tag an already-built image with one or more extra tags (e.g. semver, latest). */
def retagDockerImage(String sourceTag, List extraTags) {
    extraTags.each { t ->
        echo "[buildHelpers] Tagging ${sourceTag} -> ${t}"
        sh "docker tag ${sourceTag} ${t}"
    }
}

return this
