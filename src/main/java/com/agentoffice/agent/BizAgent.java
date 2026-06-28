package com.agentoffice.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Core interface all business agents must implement.
 */
public interface BizAgent {

    String getAgentCode();

    AgentCapability getCapability();

    AgentResult execute(String itemId, String content, Map<String, Object> globalContext);

    boolean healthCheck();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    class AgentCapability {
        private List<String> capabilities;
        private List<String> inputFormats;
        private List<String> outputFormats;
        private List<String> supportedModels;
        private List<String> preconditions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class AgentResult {
        private boolean success;
        private String summary;
        private Object data;
        private String errorMsg;
        private long costTimeMs;

        public static AgentResult ok(String summary, Object data) {
            return AgentResult.builder().success(true).summary(summary).data(data).build();
        }

        public static AgentResult fail(String errorMsg) {
            return AgentResult.builder().success(false).errorMsg(errorMsg).build();
        }
    }
}
