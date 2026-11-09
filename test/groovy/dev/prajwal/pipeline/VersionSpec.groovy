package dev.prajwal.pipeline

import spock.lang.Specification

class VersionSpec extends Specification {

    def "release tags become plain versions"() {
        expect:
        Version.imageTag(null, 'v1.4.2', 'abc1234def', '9') == '1.4.2'
    }

    def "other builds are traceable to branch, commit and build"() {
        expect:
        Version.imageTag('main', null, '3f9c2ab77e01', '57') == 'main-3f9c2ab-57'
        Version.imageTag('feature/JIRA-12_Login Page', null, '3f9c2ab77e01', '3') == 'feature-jira-12_login-page-3f9c2ab-3'
    }

    def "very long branch names are cut to keep tags valid"() {
        expect:
        Version.sanitize('x' * 100).length() == 40
    }

    def "a missing commit sha is an error, not a silently wrong tag"() {
        when:
        Version.imageTag('main', null, '', '1')

        then:
        thrown(IllegalArgumentException)
    }
}
