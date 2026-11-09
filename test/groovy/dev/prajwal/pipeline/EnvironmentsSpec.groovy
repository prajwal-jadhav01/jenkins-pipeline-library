package dev.prajwal.pipeline

import spock.lang.Specification
import spock.lang.Unroll

class EnvironmentsSpec extends Specification {

    def cfg = PipelineConfig.from(appName: 'claims-api', appType: 'dotnet-api', registry: 'r.io/team',
            environments: [dev: [host: 'dev-1'], staging: [host: 'stg-1'], prod: [host: 'prod-1']])

    @Unroll
    def "branch=#branch tag=#tag pr=#pr manual=#manual deploys to #expected"() {
        expect:
        Environments.resolve(cfg, branch, tag, pr, manual)?.name == expected

        where:
        branch          | tag      | pr   | manual    || expected
        'main'          | null     | null | 'auto'    || 'dev'
        'release/2.3'   | null     | null | 'auto'    || 'staging'
        'feature/login' | null     | null | 'auto'    || null
        'PR-12'         | null     | '12' | 'auto'    || null
        'PR-12'         | null     | '12' | 'dev'     || null     // pull requests never deploy
        null            | 'v1.4.2' | null | 'auto'    || 'prod'
        null            | 'nightly'| null | 'auto'    || null     // only version tags reach prod
        'feature/login' | null     | null | 'dev'     || 'dev'    // manual redeploy
        'main'          | null     | null | 'none'    || null
    }

    def "only prod needs approval"() {
        expect:
        Environments.resolve(cfg, null, 'v2.0.0', null, 'auto').needsApproval
        !Environments.resolve(cfg, 'main', null, null, 'auto').needsApproval
    }

    def "prod cannot be chosen by hand"() {
        when:
        Environments.resolve(cfg, 'main', null, null, 'prod')

        then:
        thrown(IllegalArgumentException)
    }

    def "an app without a staging environment just skips the deploy"() {
        given:
        def devOnly = PipelineConfig.from(appName: 'tool', appType: 'node-ui', registry: 'r.io/team',
                environments: [dev: [host: 'dev-1']])

        expect:
        Environments.resolve(devOnly, 'release/1.0', null, null, 'auto') == null
    }
}
