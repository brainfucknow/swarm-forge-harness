package mutate4java.engine;

import mutate4java.project.*;
import mutate4java.report.*;

import mutate4java.model.*;

import mutate4java.*;
import mutate4java.analysis.*;
import mutate4java.cli.*;
import mutate4java.coverage.*;
import mutate4java.exec.*;
import mutate4java.manifest.*;
import mutate4java.selection.*;

import mutate4java.selection.*;

import mutate4java.analysis.*;
import mutate4java.coverage.*;
import mutate4java.exec.*;
import mutate4java.manifest.*;

import java.io.PrintStream;
import java.nio.file.Path;

final class BaselineRunner {

    private final CoverageRunner coverageRunner;
    private final PrintStream err;

    BaselineRunner(CoverageRunner coverageRunner, PrintStream err) {
        this.coverageRunner = coverageRunner;
        this.err = err;
    }

    CoverageRun run(CliArguments parsed,
                    TestCommandExecutor executor,
                    Path moduleRoot,
                    ProgressReporter progressReporter) throws Exception {
        progressReporter.baselineStarting(moduleRoot);
        CoverageRun coverageRun;
        if (parsed.testCommand() == null) {
            if (parsed.reuseCoverage()) {
                TestRun baseline = executor.runTests(moduleRoot, 0L);
                CoverageRun reusedCoverage = coverageRunner.generateCoverage(moduleRoot, true);
                coverageRun = new CoverageRun(baseline, reusedCoverage.report(), true, reusedCoverage.reportAvailable());
                printReuseMessage(moduleRoot, coverageRun.reportAvailable());
            } else {
                coverageRun = coverageRunner.generateCoverage(moduleRoot, false);
            }
        } else {
            coverageRun = new CoverageRun(executor.runTests(moduleRoot, 0L), CoverageReport.allCovered(), false, false);
        }
        progressReporter.baselineFinished(coverageRun.baseline());
        return coverageRun;
    }

    int fail(TestRun baseline) {
        if (baseline.timedOut()) {
            err.println("Baseline tests timed out.");
        }
        err.println("Baseline tests failed.");
        err.print(baseline.output());
        return 2;
    }

    private void printReuseMessage(Path moduleRoot, boolean reportAvailable) {
        Path reportPath = moduleRoot.resolve("target/site/jacoco/jacoco.xml");
        if (reportAvailable) {
            err.printf("Reusing existing coverage data from %s.%n", reportPath);
            err.println("Warning: coverage may be stale; covered/uncovered site classification may be inaccurate.");
            return;
        }
        err.printf("Coverage reuse requested, but %s does not exist. Continuing without coverage filtering.%n", reportPath);
    }
}
