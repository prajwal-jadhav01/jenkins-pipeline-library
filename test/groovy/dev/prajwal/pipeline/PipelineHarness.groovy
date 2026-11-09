package dev.prajwal.pipeline

/**
 * Just enough of Jenkins to run vars/deliveryPipeline.groovy in a unit test. Every pipeline step is
 * recorded instead of executed, so a test can assert which stages ran and which commands were sent.
 */
abstract class PipelineHarness extends Script {

    static final Set<String> BLOCK_STEPS = ['node', 'withCredentials', 'sshagent', 'timeout'] as Set

    List<String> stages = []
    List<String> commands = []
    List<Map> calls = []

    def stage(String name, Closure body) {
        stages << name
        body()
    }

    def sh(arg) {
        String script = arg instanceof Map ? arg.script : arg
        commands << script.replaceAll(/\s+/, ' ').trim()
        if (arg instanceof Map && arg.returnStdout) {
            return '3f9c2ab77e01d4c5b6a7980f1e2d3c4b5a697887\n'
        }
        null
    }

    def libraryResource(String path) {
        def stream = getClass().classLoader.getResourceAsStream(path)
        if (stream == null) throw new IllegalStateException("no library resource ${path}")
        stream.text
    }

    def methodMissing(String name, Object args) {
        Object[] argv = args as Object[]
        calls << [step: name, args: argv.toList()]
        if (name in BLOCK_STEPS && argv && argv[-1] instanceof Closure) {
            return (argv[-1] as Closure).call()
        }
        [step: name]
    }

    boolean called(String step) {
        calls.any { it.step == step }
    }
}
