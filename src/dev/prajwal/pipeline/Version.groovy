package dev.prajwal.pipeline

/** Works out the image tag for a build, so every image can be traced back to its commit. */
class Version implements Serializable {

    private static final String RELEASE_TAG = /v(\d+)\.(\d+)\.(\d+)/

    static boolean isRelease(String tag) {
        tag ==~ RELEASE_TAG
    }

    /**
     * Release tags become plain versions: {@code v1.4.2 -> 1.4.2}.
     * Everything else gets {@code <branch>-<short sha>-<build number>}, e.g. {@code main-3f9c2ab-57}.
     */
    static String imageTag(String branch, String tag, String commitSha, String buildNumber) {
        if (tag && isRelease(tag)) {
            return tag.substring(1)
        }
        if (!commitSha || commitSha.length() < 7) {
            throw new IllegalArgumentException("need a commit sha to tag the image, got '${commitSha}'")
        }
        "${sanitize(branch ?: 'detached')}-${commitSha.substring(0, 7)}-${buildNumber}"
    }

    /** Docker tags allow [A-Za-z0-9_.-], max 128 chars. Branch names allow much more. */
    static String sanitize(String branch) {
        String clean = branch.toLowerCase().replaceAll(/[^a-z0-9_.-]+/, '-').replaceAll(/^[-.]+|-+$/, '')
        clean.length() > 40 ? clean.substring(0, 40) : clean
    }
}
