package dev.prajwal.pipeline

/**
 * Decides where a build goes, from the branch or tag that triggered it. This one rule set replaces
 * a Jenkinsfile per app per environment.
 *
 * <ul>
 *   <li>Pull requests and feature branches: build and test only.</li>
 *   <li>{@code main}: deploy to dev automatically.</li>
 *   <li>{@code release/*}: deploy to staging automatically.</li>
 *   <li>Version tags ({@code v1.4.2}): deploy to prod, after a person approves.</li>
 * </ul>
 * A manual run can redeploy to dev or staging; prod is only ever reached from a version tag.
 */
class Environments implements Serializable {

    static final List<String> NAMES = ['dev', 'staging', 'prod'].asImmutable()

    static class Target implements Serializable {
        String name
        String host
        boolean needsApproval

        String toString() { "${name} (${host})" }
    }

    /**
     * @param branch BRANCH_NAME from Jenkins (null for tag builds on some setups)
     * @param tag TAG_NAME from Jenkins, if this build is for a tag
     * @param changeId CHANGE_ID from Jenkins, set for pull request builds
     * @param manualTarget the DEPLOY_TO build parameter: 'auto', 'none', 'dev' or 'staging'
     */
    static Target resolve(PipelineConfig cfg, String branch, String tag, String changeId, String manualTarget) {
        String env = pick(branch, tag, changeId, manualTarget)
        if (env == null) {
            return null
        }
        Map target = cfg.environments[env]
        if (!target) {
            // The app doesn't deploy to this environment (e.g. no staging). Build and push only.
            return null
        }
        return new Target(name: env, host: target.host as String, needsApproval: env == 'prod')
    }

    private static String pick(String branch, String tag, String changeId, String manualTarget) {
        if (manualTarget == 'none') return null
        if (changeId) return null // never deploy pull requests
        if (tag) return Version.isRelease(tag) ? 'prod' : null
        if (manualTarget in ['dev', 'staging']) return manualTarget
        if (manualTarget && manualTarget != 'auto') {
            throw new IllegalArgumentException("DEPLOY_TO=${manualTarget} is not allowed; prod deploys come from version tags")
        }
        if (branch == 'main') return 'dev'
        if (branch?.startsWith('release/')) return 'staging'
        return null
    }
}
