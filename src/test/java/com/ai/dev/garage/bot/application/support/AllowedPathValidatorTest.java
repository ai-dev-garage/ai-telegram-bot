package com.ai.dev.garage.bot.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.dev.garage.bot.config.RunnerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AllowedPathValidatorTest {

    @TempDir
    Path tempParent;

    @Test
    void shouldAllowCwdWhenNullOrBlank() {
        RunnerProperties props = new RunnerProperties();
        props.setAllowedNavigationPaths(tempParent.toString());
        AllowedPathValidator v = new AllowedPathValidator(props);
        assertThat(v.isAllowedCwd(null)).isTrue();
        assertThat(v.isAllowedCwd("  ")).isTrue();
    }

    @Test
    void shouldRejectCwdWhenAllowlistEmpty() {
        RunnerProperties props = new RunnerProperties();
        props.setAllowedNavigationPaths("");
        AllowedPathValidator v = new AllowedPathValidator(props);
        assertThat(v.isAllowedCwd(tempParent.toString())).isFalse();
    }

    @Test
    void shouldAllowCwdWhenChildOfAllowlistedRoot() throws Exception {
        Path root = tempParent.resolve("root");
        Path child = root.resolve("sub");
        Files.createDirectories(child);
        RunnerProperties props = new RunnerProperties();
        props.setAllowedNavigationPaths(root.toString());
        AllowedPathValidator v = new AllowedPathValidator(props);
        assertThat(v.isAllowedCwd(child.toString())).isTrue();
    }

    @Test
    void shouldRejectCwdWhenOutsideAllowlistedRoot() throws Exception {
        Path allowed = tempParent.resolve("allowed");
        Path other = tempParent.resolve("other");
        Files.createDirectories(allowed);
        Files.createDirectories(other);
        RunnerProperties props = new RunnerProperties();
        props.setAllowedNavigationPaths(allowed.toString());
        AllowedPathValidator v = new AllowedPathValidator(props);
        assertThat(v.isAllowedCwd(other.toString())).isFalse();
    }
}
