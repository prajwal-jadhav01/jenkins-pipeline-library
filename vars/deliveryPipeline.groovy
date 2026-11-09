import dev.prajwal.pipeline.Environments
import dev.prajwal.pipeline.PipelineConfig
import dev.prajwal.pipeline.Version

/**
 * The one pipeline every app uses: test, build an image, push it, deploy it.
 *
 * Usage, as an app's entire Jenkinsfile:
 * <pre>
 * @Library('pipeline-library@v1') _
 * deliveryPipeline(
 *     appName: 'claims-api',
 *     appType: 'dotnet-api',
 *     registry: 'registry.example.com/team',
 *     environments: [dev: [host: 'dev-app-1'], staging: [host: 'stg-app-1'], prod: [host: 'prod-app-1']]
 * )
 * </pre>
 */
def call(Map args) {
    PipelineConfig cfg = PipelineConfig.from(args)

    properties([
        buildDiscarder(logRotator(numToKeepStr: '30')),
        disableConcurrentBuilds(),
        parameters([
            choice(name: 'DEPLOY_TO', choices: ['auto', 'none', 'dev', 'staging'],
                   description: 'auto = follow the branch rules. Prod only deploys from a version tag.'),
        ]),
    ])

    node {
        String imageRef = null
        Environments.Target target = null

        try {
            stage('Checkout') {
                checkout scm
                String sha = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                String tag = Version.imageTag(env.BRANCH_NAME, env.TAG_NAME, sha, env.BUILD_NUMBER)
                imageRef = "${cfg.imageRepository}:${tag}"
                target = Environments.resolve(cfg, env.BRANCH_NAME, env.TAG_NAME, env.CHANGE_ID,
                                              params.DEPLOY_TO ?: 'auto')
                currentBuild.description = target ? "${tag} -> ${target.name}" : tag
                echo "Image: ${imageRef}; deploy target: ${target ?: 'none'}"
            }

            if (cfg.testCommand) {
                stage('Test') {
                    sh cfg.testCommand
                }
            }

            stage('Build image') {
                String dockerfile = cfg.dockerfile
                if (cfg.usesTemplateDockerfile()) {
                    dockerfile = '.pipeline.Dockerfile'
                    writeFile file: dockerfile,
                              text: libraryResource("dev/prajwal/pipeline/dockerfiles/${cfg.templateDockerfile}")
                }
                String buildArgs = cfg.buildArgs.collect { k, v -> "--build-arg ${k}='${v}'" }.join(' ')
                sh """docker build --pull \
                    --label org.opencontainers.image.revision=\$(git rev-parse HEAD) \
                    --label org.opencontainers.image.source=${env.GIT_URL ?: 'unknown'} \
                    --build-arg APP_NAME=${cfg.appName} ${buildArgs} \
                    -f ${dockerfile} -t ${imageRef} ${cfg.buildContext}"""
            }

            // Pull request images are built to prove the Dockerfile works, but never pushed.
            if (!env.CHANGE_ID) {
                stage('Push image') {
                    withCredentials([usernamePassword(credentialsId: cfg.registryCredentialsId,
                                                      usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
                        String registryHost = cfg.registry.tokenize('/')[0]
                        sh 'echo "$REG_PASS" | docker login -u "$REG_USER" --password-stdin ' + registryHost
                        sh "docker push ${imageRef}"
                    }
                }
            }

            if (target) {
                stage("Deploy to ${target.name}") {
                    if (target.needsApproval) {
                        timeout(time: 4, unit: 'HOURS') {
                            input message: "Deploy ${imageRef} to production?", ok: 'Deploy',
                                  submitter: cfg.prodApprovers
                        }
                    }
                    deployImage(cfg, target, imageRef)
                }
            }
        } catch (err) {
            currentBuild.result = 'FAILURE'
            throw err
        } finally {
            if (imageRef) {
                sh "docker image rm ${imageRef} || true"
            }
            cleanWs()
        }
    }
}

/** Ships the image to the target host and waits for it to report healthy; rolls back if it doesn't. */
private void deployImage(PipelineConfig cfg, Environments.Target target, String imageRef) {
    writeFile file: '.pipeline-deploy.sh', text: libraryResource('dev/prajwal/pipeline/deploy/deploy.sh')
    sshagent(credentials: ["deploy-${target.name}"]) {
        sh """scp -o StrictHostKeyChecking=accept-new .pipeline-deploy.sh deploy@${target.host}:/tmp/deploy-${cfg.appName}.sh
              ssh -o StrictHostKeyChecking=accept-new deploy@${target.host} \
                bash /tmp/deploy-${cfg.appName}.sh '${cfg.appName}' '${imageRef}' '${cfg.port}' '${cfg.hostPort}' '${cfg.healthPath}'"""
    }
}
