package dev.prajwal.pipeline

import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Specification

class DeliveryPipelineSpec extends Specification {

    static final Map APP = [appName: 'claims-api', appType: 'dotnet-api', registry: 'registry.example.com/team',
                            environments: [dev: [host: 'dev-app-1'], prod: [host: 'prod-app-1']]]

    PipelineHarness run(Map env, Map args = APP, Map params = [:]) {
        def compiler = new CompilerConfiguration(scriptBaseClass: PipelineHarness.name)
        def binding = new Binding(env: [BUILD_NUMBER: '57', GIT_URL: 'https://git.example.com/claims-api.git'] + env,
                                  params: params, currentBuild: [:], scm: 'scm')
        def text = getClass().classLoader.getResource('deliveryPipeline.groovy').text
        def script = new GroovyShell(getClass().classLoader, binding, compiler).parse(text) as PipelineHarness
        script.call(args)
        script
    }

    def "main builds, pushes and deploys to dev"() {
        when:
        def p = run(BRANCH_NAME: 'main')

        then:
        p.stages == ['Checkout', 'Test', 'Build image', 'Push image', 'Deploy to dev']
        p.commands.contains('dotnet test --configuration Release')
        p.commands.any { it.startsWith('docker build') && it.contains('-t registry.example.com/team/claims-api:main-3f9c2ab-57') }
        p.commands.contains('docker push registry.example.com/team/claims-api:main-3f9c2ab-57')
        p.commands.any { it.contains('ssh') && it.contains('deploy@dev-app-1') }
        !p.called('input')
        p.binding.currentBuild.description == 'main-3f9c2ab-57 -> dev'
    }

    def "pull requests build the image but never push or deploy"() {
        when:
        def p = run(BRANCH_NAME: 'PR-12', CHANGE_ID: '12')

        then:
        p.stages == ['Checkout', 'Test', 'Build image']
        !p.commands.any { it.startsWith('docker push') }
    }

    def "a version tag goes to prod only after approval"() {
        when:
        def p = run(TAG_NAME: 'v1.4.2')

        then:
        p.stages.last() == 'Deploy to prod'
        def approval = p.calls.find { it.step == 'input' }
        approval.args[0].submitter == 'release-managers'
        p.commands.any { it.contains("deploy@prod-app-1") && it.contains("'registry.example.com/team/claims-api:1.4.2'") }
    }

    def "the template Dockerfile is used when the app has none"() {
        when:
        def p = run(BRANCH_NAME: 'feature/login')

        then:
        def written = p.calls.find { it.step == 'writeFile' }.args[0]
        written.file == '.pipeline.Dockerfile'
        written.text.contains('mcr.microsoft.com/dotnet/sdk')
        !p.stages.any { it.startsWith('Deploy') }
    }

    def "the local image and workspace are cleaned up even when a stage fails"() {
        given:
        def failingApp = APP + [testCommand: 'exit 1']

        when:
        def compiler = new CompilerConfiguration(scriptBaseClass: FailingTestsHarness.name)
        def binding = new Binding(env: [BRANCH_NAME: 'main', BUILD_NUMBER: '1'], params: [:], currentBuild: [:], scm: 'scm')
        def script = new GroovyShell(getClass().classLoader, binding, compiler)
                .parse(getClass().classLoader.getResource('deliveryPipeline.groovy').text) as PipelineHarness
        script.call(failingApp)

        then:
        def e = thrown(RuntimeException)
        e.message == 'script returned exit code 1'
        script.stages == ['Checkout', 'Test']
        binding.getVariable('currentBuild').result == 'FAILURE'
        script.commands.last() == 'docker image rm registry.example.com/team/claims-api:main-3f9c2ab-1 || true'
        script.called('cleanWs')
    }

}
