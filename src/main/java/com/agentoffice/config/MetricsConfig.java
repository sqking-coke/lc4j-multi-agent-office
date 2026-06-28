package com.agentoffice.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer 指标配置：注册 Agent 任务计数、错误计数、耗时分布和 LLM Token 消耗等 Prometheus 指标。
 */
@Configuration
public class MetricsConfig {

    @Bean
    public AgentMetrics agentMetrics(MeterRegistry meterRegistry) {
        return new AgentMetrics(meterRegistry);
    }

    public static class AgentMetrics {
        private final MeterRegistry registry;
        private final ConcurrentHashMap<String, Counter> taskCounters = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Counter> errorCounters = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Timer> durationTimers = new ConcurrentHashMap<>();

        public AgentMetrics(MeterRegistry registry) {
            this.registry = registry;
        }

        public void recordTask(String agentCode, boolean success, long durationMs) {
            taskCounters.computeIfAbsent(agentCode,
                    k -> Counter.builder("agent_task_total")
                            .tag("agent", agentCode)
                            .register(registry))
                    .increment();

            if (!success) {
                errorCounters.computeIfAbsent(agentCode,
                        k -> Counter.builder("agent_task_errors")
                                .tag("agent", agentCode)
                                .register(registry))
                        .increment();
            }

            durationTimers.computeIfAbsent(agentCode,
                    k -> Timer.builder("agent_task_duration")
                            .tag("agent", agentCode)
                            .register(registry))
                    .record(durationMs, TimeUnit.MILLISECONDS);
        }

        public void recordLlmCall(String agentCode, String model, int tokens) {
            Counter.builder("llm_token_consumed")
                    .tag("agent", agentCode)
                    .tag("model", model != null ? model : "unknown")
                    .register(registry)
                    .increment(tokens);
        }
    }
}
