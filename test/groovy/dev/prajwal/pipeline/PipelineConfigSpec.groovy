package dev.prajwal.pipeline

import spock.lang.Specification

class PipelineConfigSpec extends Specification {

    def "applies app-type defaults so most apps need three settings"() {
        when:
        def cfg = PipelineConfig.from(appName: 'claims-api', appType: 'dotnet-api', registry: 'registry.example.com/team/')

        then:
        cfg.imageRepository == 'registry.example.com/team/claims-api'
        cfg.testCommand == 'dotnet test --configuration Release'
        cfg.healthPath == '/health'
        cfg.port == 8080
        cfg.hostPort == 8080
        cfg.usesTemplateDockerfile()
        cfg.templateDockerfile == 'dotnet-api.Dockerfile'
    }

    def "an app can override defaults, including switching tests off"() {
        when:
        def cfg = PipelineConfig.from(appName: 'web-ui', appType: 'node-ui', registry: 'r.io/x',
                testCommand: '', dockerfile: 'docker/Dockerfile', hostPort: 8085,
                buildArgs: [BUILD_DIR: 'build'])

        then:
        cfg.testCommand == ''
        !cfg.usesTemplateDockerfile()
        cfg.port == 80
        cfg.hostPort == 8085
        cfg.buildArgs == [BUILD_DIR: 'build']
    }

    def "reports every problem at once"() {
        when:
        PipelineConfig.from(appName: 'Claims API', appType: 'cobol', typo: true,
                environments: [qa: [host: 'x'], prod: [:]])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("'registry' is required")
        e.message.contains("unknown appType 'cobol'")
        e.message.contains("appName 'Claims API' must be lowercase")
        e.message.contains('unknown setting(s): typo')
        e.message.contains("unknown environment 'qa'")
        e.message.contains("environment 'prod' needs a host")
    }
}
