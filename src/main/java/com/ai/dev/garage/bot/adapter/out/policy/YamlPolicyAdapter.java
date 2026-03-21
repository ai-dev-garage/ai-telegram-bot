package com.ai.dev.garage.bot.adapter.out.policy;

import com.ai.dev.garage.bot.application.port.out.PolicyProvider;
import com.ai.dev.garage.bot.config.RunnerProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@Service
@RequiredArgsConstructor
public class YamlPolicyAdapter implements PolicyProvider {
    private final RunnerProperties runnerProperties;
    private final Yaml yaml = new Yaml();

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadPolicy() {
        String policyPath = runnerProperties.getPolicyPath();
        if (policyPath != null && !policyPath.isBlank()) {
            try {
                return yaml.load(Files.readString(Path.of(policyPath)));
            } catch (IOException ex) {
                log.warn("Could not read policy file {}: {}", policyPath, ex.getMessage());
            }
        }
        try {
            ClassPathResource resource = new ClassPathResource("policy-tiers.yaml");
            return yaml.load(resource.getInputStream());
        } catch (IOException e) {
            log.warn("Using embedded default policy (classpath policy-tiers.yaml unavailable): {}", e.getMessage());
            return Map.of(
                "allowlist_safe", Map.of("shell_command", List.of()),
                "require_approval_patterns", Map.of("shell_command", List.of("sudo", "rm -rf"))
            );
        }
    }
}
