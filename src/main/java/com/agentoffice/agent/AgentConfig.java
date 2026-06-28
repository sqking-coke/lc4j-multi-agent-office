package com.agentoffice.agent;

import lombok.Builder;
import lombok.Data;

/** Agent runtime configuration: model, temperature, token budget, retry strategy, concurrency limits. */
@Data
@Builder
public class AgentConfig {
    private String agentCode;
    private String agentName;
    private BizAgent.AgentCapability capability;
    private String model;
    private double temperature;
    private int maxTokens;
    private long dailyTokenBudget;
    private int retryMax;
    private long retryBackoffMs;
    private int timeoutSeconds;
    private int priority;
    private int maxConcurrency;
}
