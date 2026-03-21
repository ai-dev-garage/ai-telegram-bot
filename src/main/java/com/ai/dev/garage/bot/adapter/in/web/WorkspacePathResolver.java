package com.ai.dev.garage.bot.adapter.in.web;

import com.ai.dev.garage.bot.config.RunnerProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspacePathResolver {

    private final RunnerProperties runnerProperties;

    public List<WorkspacePath> resolve() {
        List<String> raw = runnerProperties.listAllowedNavigationPaths();
        List<WorkspacePath> paths = new ArrayList<>();
        for (String s : raw) {
            String expanded = expandHome(s.trim());
            try {
                Path real = Path.of(expanded).toRealPath();
                Path name = real.getFileName();
                String label = name != null ? name.toString() : real.toString();
                paths.add(new WorkspacePath(real.toString(), label));
            } catch (IOException ignored) {
            }
        }
        return paths;
    }

    private static String expandHome(String path) {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        if ("~".equals(path)) {
            return System.getProperty("user.home");
        }
        return path;
    }

    public record WorkspacePath(String path, String label) {}
}
