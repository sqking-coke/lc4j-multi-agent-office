package com.agentoffice.prompt;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.entity.AgentPromptTemplate;
import com.agentoffice.mapper.AgentPromptTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {{variable}} placeholders in Prompt templates at runtime.
 * Falls back to the provided hardcoded prompt when no DB template exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(.+?)}}");

    private final AgentPromptTemplateMapper promptTemplateMapper;
    private final AgentRegistry agentRegistry;

    /**
     * Resolve a prompt template for the given agent.
     *
     * @param agentCode       agent identifier for DB template lookup
     * @param fallbackPrompt  hardcoded prompt used when no DB template exists
     * @param taskParams      task-level variables (e.g. content, language)
     * @param globalContext   shared context across agents
     * @return resolved prompt string
     */
    public String resolve(String agentCode, String fallbackPrompt,
                          Map<String, Object> taskParams,
                          Map<String, Object> globalContext) {
        AgentPromptTemplate template = promptTemplateMapper.selectOne(
                new LambdaQueryWrapper<AgentPromptTemplate>()
                        .eq(AgentPromptTemplate::getAgentCode, agentCode)
                        .eq(AgentPromptTemplate::getStatus, 1)
                        .orderByDesc(AgentPromptTemplate::getCreateTime)
                        .last("LIMIT 1"));

        if (template == null) {
            return fallbackPrompt;
        }

        String content = template.getTemplateContent();
        Matcher matcher = PLACEHOLDER.matcher(content);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String variable = matcher.group(1).trim();
            String value = resolveVariable(variable, taskParams, globalContext, agentCode);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : matcher.group(0)));
        }
        matcher.appendTail(sb);

        log.debug("Resolved prompt for agent {} using template version {}",
                agentCode, template.getVersion());
        return sb.toString();
    }

    private String resolveVariable(String variable, Map<String, Object> taskParams,
                                   Map<String, Object> globalContext, String agentCode) {
        // 1. task.* — from task params
        if (variable.startsWith("task.")) {
            String key = variable.substring(5);
            Object value = taskParams != null ? taskParams.get(key) : null;
            return value != null ? value.toString() : null;
        }

        // 2. global.* — from global context
        if (variable.startsWith("global.")) {
            String key = variable.substring(7);
            Object value = globalContext != null ? globalContext.get(key) : null;
            return value != null ? value.toString() : null;
        }

        // 3. agent.config.* — from agent config
        if (variable.startsWith("agent.config.")) {
            String field = variable.substring(13);
            return resolveConfigField(agentCode, field);
        }

        // 4. bare variable — try task params first, then global context
        if (taskParams != null && taskParams.containsKey(variable)) {
            Object value = taskParams.get(variable);
            return value != null ? value.toString() : null;
        }
        if (globalContext != null && globalContext.containsKey(variable)) {
            Object value = globalContext.get(variable);
            return value != null ? value.toString() : null;
        }

        return null;
    }

    private String resolveConfigField(String agentCode, String field) {
        return agentRegistry.getConfig(agentCode)
                .map(config -> switch (field) {
                    case "model" -> config.getModel();
                    case "temperature" -> String.valueOf(config.getTemperature());
                    case "maxTokens" -> String.valueOf(config.getMaxTokens());
                    case "dailyTokenBudget" -> String.valueOf(config.getDailyTokenBudget());
                    case "retryMax" -> String.valueOf(config.getRetryMax());
                    case "timeoutSeconds" -> String.valueOf(config.getTimeoutSeconds());
                    case "priority" -> String.valueOf(config.getPriority());
                    case "maxConcurrency" -> String.valueOf(config.getMaxConcurrency());
                    default -> null;
                })
                .orElse(null);
    }
}
