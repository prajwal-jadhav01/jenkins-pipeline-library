package dev.prajwal.pipeline

/**
 * Everything one app needs to tell the shared pipeline. An app's whole Jenkinsfile is a call like:
 *
 * <pre>
 * deliveryPipeline(appName: 'claims-api', appType: 'dotnet-api', registry: 'registry.example.com/team')
 * </pre>
 *
 * Defaults come from the app type, so most apps only set three values. Anything wrong is reported
 * up front, before a single stage runs.
 */
class PipelineConfig implements Serializable {

    /** Build/test defaults per kind of app. These replace the per-app Jenkinsfile copies. */
    static final Map<String, Map> APP_TYPES = [
        'dotnet-api'  : [testCommand: 'dotnet test --configuration Release', dockerfile: 'dotnet-api.Dockerfile',
                         healthPath : '/health', port: 8080],
        'java-service': [testCommand: 'mvn -B verify', dockerfile: 'java-service.Dockerfile',
                         healthPath : '/actuator/health', port: 8080],
        'node-ui'     : [testCommand: 'npm ci && npm test -- --watch=false', dockerfile: 'node-ui.Dockerfile',
                         healthPath : '/', port: 80],
    ].asImmutable()

    String appName
    String appType
    String registry
    String registryCredentialsId = 'docker-registry'
    /** Shell command for tests. Null means use the app type's default; empty string skips tests. */
    String testCommand
    /** Path to the app's own Dockerfile. When null, the library's template for the app type is used. */
    String dockerfile
    String buildContext = '.'
    String healthPath
    /** Port the app listens on inside the container. */
    int port
    /** Port published on the deploy host (e.g. what nginx proxies to). Defaults to {@link #port}. */
    int hostPort
    /** Extra --build-arg values, e.g. [PROJECT: 'src/Claims.Api/Claims.Api.csproj']. */
    Map<String, String> buildArgs = [:]
    /** Deploy targets, e.g. [dev: [host: 'dev-app-1'], prod: [host: 'prod-app-1']]. */
    Map<String, Map> environments = [:]
    /** Jenkins user or group allowed to approve production deploys. */
    String prodApprovers = 'release-managers'

    static PipelineConfig from(Map args) {
        List<String> errors = []
        ['appName', 'appType', 'registry'].each { key ->
            if (!args[key]) errors << "'${key}' is required"
        }
        if (args.appType && !APP_TYPES.containsKey(args.appType)) {
            errors << "unknown appType '${args.appType}' (expected one of ${APP_TYPES.keySet().join(', ')})"
        }
        if (args.appName && !(args.appName ==~ /[a-z0-9][a-z0-9-]{1,62}/)) {
            errors << "appName '${args.appName}' must be lowercase letters, digits and dashes (it becomes the image name)"
        }
        def unknownKeys = args.keySet() - KNOWN_KEYS
        if (unknownKeys) {
            errors << "unknown setting(s): ${unknownKeys.sort().join(', ')}"
        }
        (args.environments ?: [:]).each { String env, Map target ->
            if (!Environments.NAMES.contains(env)) errors << "unknown environment '${env}'"
            if (!target?.host) errors << "environment '${env}' needs a host"
        }
        if (errors) {
            throw new IllegalArgumentException("deliveryPipeline configuration problems:\n - " + errors.join('\n - '))
        }

        Map defaults = APP_TYPES[args.appType as String]
        def cfg = new PipelineConfig()
        cfg.appName = args.appName
        cfg.appType = args.appType
        cfg.registry = (args.registry as String).replaceAll('/+$', '')
        cfg.registryCredentialsId = args.registryCredentialsId ?: cfg.registryCredentialsId
        cfg.testCommand = args.containsKey('testCommand') ? args.testCommand : defaults.testCommand
        cfg.dockerfile = args.dockerfile
        cfg.buildContext = args.buildContext ?: '.'
        cfg.healthPath = args.healthPath ?: defaults.healthPath
        cfg.port = (args.port ?: defaults.port) as int
        cfg.hostPort = (args.hostPort ?: cfg.port) as int
        cfg.buildArgs = (args.buildArgs ?: [:]) as Map<String, String>
        cfg.environments = (args.environments ?: [:]) as Map<String, Map>
        cfg.prodApprovers = args.prodApprovers ?: cfg.prodApprovers
        return cfg
    }

    static final Set<String> KNOWN_KEYS = ['appName', 'appType', 'registry', 'registryCredentialsId', 'testCommand',
                                           'dockerfile', 'buildContext', 'healthPath', 'port', 'hostPort', 'buildArgs',
                                           'environments',
                                           'prodApprovers'] as Set

    String getImageRepository() {
        "${registry}/${appName}"
    }

    boolean usesTemplateDockerfile() {
        dockerfile == null
    }

    String getTemplateDockerfile() {
        APP_TYPES[appType].dockerfile
    }
}
