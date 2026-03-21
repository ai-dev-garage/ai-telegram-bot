package com.ai.dev.garage.bot;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.dev.garage.bot.adapter.out.claude.ClaudeCliAdapter;
import com.ai.dev.garage.bot.adapter.out.claude.ClaudePlanCliAdapter;
import com.ai.dev.garage.bot.adapter.out.cursor.CursorPlanCliAdapter;
import com.ai.dev.garage.bot.application.port.out.AgentTaskRuntime;
import com.ai.dev.garage.bot.application.port.out.PlanCliRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Ensures the Spring context starts with {@code AGENT_RUNTIME=claude} and wires {@link PlanCliRuntime}
 * (previously missing, which broke plan mode and startup).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
class RunnerClaudeRuntimeContextTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.runner.agent-runtime", () -> "claude");
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsWithSinglePlanCliRuntimeAndClaudeAdapters() {
        assertThat(context.getBeansOfType(PlanCliRuntime.class)).hasSize(1);
        assertThat(context.getBean(PlanCliRuntime.class)).isInstanceOf(ClaudePlanCliAdapter.class);

        assertThat(context.getBeansOfType(AgentTaskRuntime.class)).hasSize(1);
        assertThat(context.getBean(AgentTaskRuntime.class)).isInstanceOf(ClaudeCliAdapter.class);

        assertThat(context.getBeansOfType(CursorPlanCliAdapter.class)).isEmpty();
    }
}
