package com.ai.dev.garage.bot.application.support;

import com.ai.dev.garage.bot.config.RunnerProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedPathValidatorTest {

    @TempDir
    Path tempParent;

    @Test
    void shouldAllowCwdWhenNullOrBlank() {
        var props = new RunnerProperties();
        props.setAllowedNavigationPaths(tempParent.toString());
        var v = new AllowedPathValidator(props);
        assertThat(v.isAllowedCwd(null)).isTrue();
        assertThat(v.isAllowedCwd("  ")).isTrue();
    }

    @Test
    void shouldRejectCwdWhenAllowlistEmpty() {
        var props = new RunnerProperties();
        props.setAllowedNavigationPaths("");
        var v = new AllowedPathValidator(props);
        assertThat(v.isAllowedCwd(tempParent.toString())).isFalse();
    }

    @Test
    void shouldAllowCwdWhenChildOfAllowlistedRoot() throws Exception {
        var root = tempParent.resolve("root");
        var child = root.resolve("sub");
        Files.createDirectories(child);
        var props = new RunnerProperties();
        props.setAllowedNavigationPaths(root.toString());
        var v = new AllowedPathValidator(props);
        assertThat(v.isAllowedCwd(child.toString())).isTrue();
    }

    @Test
    void shouldRejectCwdWhenOutsideAllowlistedRoot() throws Exception {
        var allowed = tempParent.resolve("allowed");
        var other = tempParent.resolve("other");
        Files.createDirectories(allowed);
        Files.createDirectories(other);
        var props = new RunnerProperties();
        props.setAllowedNavigationPaths(allowed.toString());
        var v = new AllowedPathValidator(props);
        assertThat(v.isAllowedCwd(other.toString())).isFalse();
    }
}
