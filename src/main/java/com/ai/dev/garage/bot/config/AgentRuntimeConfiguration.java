package com.ai.dev.garage.bot.config;

import com.ai.dev.garage.bot.adapter.out.agenttask.AgentTaskFileWriter;
import com.ai.dev.garage.bot.adapter.out.claude.ClaudeCliAdapter;
import com.ai.dev.garage.bot.adapter.out.claude.ClaudePlanCliAdapter;
import com.ai.dev.garage.bot.adapter.out.claude.ClaudeWorkflowPlannerAdapter;
import com.ai.dev.garage.bot.adapter.out.cli.CliStreamParser;
import com.ai.dev.garage.bot.adapter.out.cli.CliWorkspaceResolver;
import com.ai.dev.garage.bot.adapter.out.cursor.CursorAgentModelsCliRunner;
import com.ai.dev.garage.bot.adapter.out.cursor.CursorCliAdapter;
import com.ai.dev.garage.bot.adapter.out.cursor.CursorCliModelResolver;
import com.ai.dev.garage.bot.adapter.out.cursor.CursorPlanCliAdapter;
import com.ai.dev.garage.bot.adapter.out.cursor.CursorWorkflowPlannerAdapter;
import com.ai.dev.garage.bot.application.port.out.AgentTaskRuntime;
import com.ai.dev.garage.bot.application.port.out.JobLogAppender;
import com.ai.dev.garage.bot.application.port.out.JsonCodec;
import com.ai.dev.garage.bot.application.port.out.PlanCliRuntime;
import com.ai.dev.garage.bot.application.port.out.WorkflowPlannerRuntime;
import com.ai.dev.garage.bot.application.service.AgentQuestionParser;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralised factory for agent-runtime beans. Replaces per-adapter {@code @Service} /
 * {@code @ConditionalOnProperty} annotations so that hybrid mode ({@code AGENT_RUNTIME=hybrid})
 * can wire different adapters to different ports (planner vs executor).
 *
 * <ul>
 *   <li>{@code cursor}  — only Cursor adapter beans are created (default).</li>
 *   <li>{@code claude}  — only Claude adapter beans are created.</li>
 *   <li>{@code hybrid}  — planner port uses {@code app.workflow.planner-runtime},
 *       executor port uses {@code app.workflow.executor-runtime}.</li>
 * </ul>
 */
@Configuration
public class AgentRuntimeConfiguration {

    // ─── Port: AgentTaskRuntime (who executes AGENT_TASK jobs) ──────────────

    @Bean
    @ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime",
        havingValue = "cursor", matchIfMissing = true)
    AgentTaskRuntime cursorAgentTaskRuntime(AgentTaskFileWriter writer,
                                            CursorCliProperties props,
                                            JobLogAppender logAppender,
                                            CliWorkspaceResolver workspaceResolver,
                                            CursorCliModelResolver modelResolver) {
        return new CursorCliAdapter(writer, props, logAppender, workspaceResolver, modelResolver);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime",
        havingValue = "claude")
    AgentTaskRuntime claudeAgentTaskRuntime(AgentTaskFileWriter writer,
                                            ClaudeCliProperties props,
                                            JobLogAppender logAppender,
                                            CliWorkspaceResolver workspaceResolver) {
        return new ClaudeCliAdapter(writer, props, logAppender, workspaceResolver);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime",
        havingValue = "hybrid")
    AgentTaskRuntime hybridAgentTaskRuntime(WorkflowProperties workflowProps,
                                            AgentTaskFileWriter writer,
                                            JobLogAppender logAppender,
                                            CliWorkspaceResolver workspaceResolver,
                                            CursorCliProperties cursorProps,
                                            CursorCliModelResolver cursorModelResolver,
                                            ClaudeCliProperties claudeProps) {
        return switch (workflowProps.getExecutorRuntime()) {
            case "cursor" -> new CursorCliAdapter(writer, cursorProps, logAppender,
                workspaceResolver, cursorModelResolver);
            case "claude" -> new ClaudeCliAdapter(writer, claudeProps, logAppender,
                workspaceResolver);
            default -> throw new IllegalStateException(
                "Unknown app.workflow.executor-runtime: " + workflowProps.getExecutorRuntime());
        };
    }

    // ─── Port: PlanCliRuntime (who handles /plan interactive sessions) ──────

    @Bean
    @ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime",
        havingValue = "cursor", matchIfMissing = true)
    PlanCliRuntime cursorPlanCliRuntime(CursorCliProperties props,
                                        CliWorkspaceResolver workspaceResolver,
                                        CliStreamParser streamParser,
                                        AgentQuestionParser questionParser,
                                        JobLogAppender logAppender,
                                        CursorCliModelResolver modelResolver) {
        return new CursorPlanCliAdapter(props, workspaceResolver, streamParser,
            questionParser, logAppender, modelResolver);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime",
        havingValue = "claude")
    PlanCliRuntime claudePlanCliRuntime(ClaudeCliProperties props,
                                        CliWorkspaceResolver workspaceResolver,
                                        CliStreamParser streamParser,
                                        AgentQuestionParser questionParser,
                                        JobLogAppender logAppender) {
        return new ClaudePlanCliAdapter(props, workspaceResolver, streamParser,
            questionParser, logAppender);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime",
        havingValue = "hybrid")
    PlanCliRuntime hybridPlanCliRuntime(WorkflowProperties workflowProps,
                                        CursorCliProperties cursorProps,
                                        ClaudeCliProperties claudeProps,
                                        CliWorkspaceResolver workspaceResolver,
                                        CliStreamParser streamParser,
                                        AgentQuestionParser questionParser,
                                        JobLogAppender logAppender,
                                        CursorCliModelResolver cursorModelResolver) {
        return switch (workflowProps.getPlannerRuntime()) {
            case "cursor" -> new CursorPlanCliAdapter(cursorProps, workspaceResolver,
                streamParser, questionParser, logAppender, cursorModelResolver);
            case "claude" -> new ClaudePlanCliAdapter(claudeProps, workspaceResolver,
                streamParser, questionParser, logAppender);
            default -> throw new IllegalStateException(
                "Unknown app.workflow.planner-runtime: " + workflowProps.getPlannerRuntime());
        };
    }

    // ─── Port: WorkflowPlannerRuntime (who decomposes intents into graphs) ──

    @Bean
    @ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime",
        havingValue = "cursor", matchIfMissing = true)
    WorkflowPlannerRuntime cursorWorkflowPlanner(CursorCliProperties props,
                                                  WorkflowProperties workflowProps,
                                                  JsonCodec jsonCodec) {
        return new CursorWorkflowPlannerAdapter(props, workflowProps, jsonCodec);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime",
        havingValue = "claude")
    WorkflowPlannerRuntime claudeWorkflowPlanner(ClaudeCliProperties props,
                                                  WorkflowProperties workflowProps,
                                                  JsonCodec jsonCodec) {
        return new ClaudeWorkflowPlannerAdapter(props, workflowProps, jsonCodec);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.runner", name = "agent-runtime",
        havingValue = "hybrid")
    WorkflowPlannerRuntime hybridWorkflowPlanner(WorkflowProperties workflowProps,
                                                  CursorCliProperties cursorProps,
                                                  ClaudeCliProperties claudeProps,
                                                  JsonCodec jsonCodec) {
        return switch (workflowProps.getPlannerRuntime()) {
            case "cursor" -> new CursorWorkflowPlannerAdapter(cursorProps,
                workflowProps, jsonCodec);
            case "claude" -> new ClaudeWorkflowPlannerAdapter(claudeProps,
                workflowProps, jsonCodec);
            default -> throw new IllegalStateException(
                "Unknown app.workflow.planner-runtime: " + workflowProps.getPlannerRuntime());
        };
    }

    // ─── Internal Cursor helpers (needed when cursor is the active executor) ─

    @Bean
    @ConditionalOnExpression(
        "'${app.runner.agent-runtime:cursor}' == 'cursor' or "
            + "'${app.runner.agent-runtime:cursor}' == 'hybrid'")
    CursorCliModelResolver cursorCliModelResolver(JsonCodec jsonCodec,
                                                   CursorCliProperties props) {
        return new CursorCliModelResolver(jsonCodec, props);
    }

    @Bean
    @ConditionalOnExpression(
        "'${app.runner.agent-runtime:cursor}' == 'cursor' or "
            + "'${app.runner.agent-runtime:cursor}' == 'hybrid'")
    CursorAgentModelsCliRunner cursorAgentModelsCliRunner(CursorCliProperties props) {
        return new CursorAgentModelsCliRunner(props);
    }
}
