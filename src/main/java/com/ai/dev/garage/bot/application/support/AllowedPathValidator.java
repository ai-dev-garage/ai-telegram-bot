package com.ai.dev.garage.bot.application.support;

import com.ai.dev.garage.bot.config.RunnerProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ensures a working directory is under configured allowlisted roots (canonical paths).
 */
@Component
@RequiredArgsConstructor
public class AllowedPathValidator {

    private final RunnerProperties runnerProperties;

    /**
     * @param cwd nullable; null/blank means process default directory (always allowed)
     * @return true if cwd is allowed or unset
     */
    public boolean isAllowedCwd(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return true;
        }
        List<Path> allowed = canonicalAllowlist();
        if (allowed.isEmpty()) {
            return false;
        }
        Path request;
        try {
            request = Path.of(cwd.trim()).toRealPath();
        } catch (IOException e) {
            return false;
        }
        for (Path root : allowed) {
            if (request.equals(root)) {
                return true;
            }
            if (request.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    public String validationFailureReason(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return null;
        }
        if (!isAllowedCwd(cwd)) {
            return "cwd is not under an allowlisted path: " + cwd;
        }
        return null;
    }

    private List<Path> canonicalAllowlist() {
        List<String> raw = runnerProperties.listAllowedNavigationPaths();
        if (raw.isEmpty()) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;
            }
            String expanded = expandUserHome(s.trim());
            try {
                out.add(Path.of(expanded).toRealPath());
            } catch (IOException ignored) {
                // skip missing paths
            }
        }
        return out;
    }

    private static String expandUserHome(String path) {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        if ("~".equals(path)) {
            return System.getProperty("user.home");
        }
        return path;
    }
}
