package com.enterprise.automation.listeners;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

/** Local execution metadata resolves only absolute git executables from PATH. */
@Test(groups = {"unit", "security"})
public class ExecutionMetadataTest {

    /** A relative PATH entry cannot supply the git binary used to collect local run metadata. */
    public void gitExecutableResolutionIgnoresRelativePathEntries() {
        assertThat(ExecutionMetadata.findGitExecutable("." + File.pathSeparator)).isEmpty();

        Path executable = ExecutionMetadata.findGitExecutable("." + File.pathSeparator + System.getenv("PATH"))
                .orElseThrow(() -> new AssertionError("Expected the installed git executable in absolute PATH"));

        assertThat(executable.isAbsolute()).isTrue();
        assertThat(Files.isRegularFile(executable)).isTrue();
        assertThat(Files.isExecutable(executable)).isTrue();
    }
}
