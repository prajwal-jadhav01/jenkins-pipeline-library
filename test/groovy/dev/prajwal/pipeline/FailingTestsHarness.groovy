package dev.prajwal.pipeline

/** A harness whose test command fails, to check the pipeline still cleans up after itself. */
abstract class FailingTestsHarness extends PipelineHarness {
    @Override
    def sh(arg) {
        if (arg == 'exit 1') throw new RuntimeException('script returned exit code 1')
        super.sh(arg)
    }
}
