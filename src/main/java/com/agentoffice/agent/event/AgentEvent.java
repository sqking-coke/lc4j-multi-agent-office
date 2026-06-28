package com.agentoffice.agent.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 事件基类，定义事件通用字段（类型、traceId、taskId、时间戳）。
 * 子类对应任务生命周期的各阶段事件。
 */
@Getter
@ToString
@AllArgsConstructor
public class AgentEvent {
    private final String eventType;
    private final String traceId;
    private final Long taskId;
    private final LocalDateTime timestamp;

    public static AgentEvent of(String type, String traceId, Long taskId) {
        return new AgentEvent(type, traceId, taskId, LocalDateTime.now());
    }

    /** 任务拆解完成事件，携带子任务内容列表和协同模式。 */
    @Getter
    @ToString(callSuper = true)
    public static class TaskDecomposed extends AgentEvent {
        private final List<String> subTaskContents;
        private final String coopMode;

        public TaskDecomposed(String traceId, Long taskId, List<String> subTaskContents, String coopMode) {
            super("TASK_DECOMPOSED", traceId, taskId, LocalDateTime.now());
            this.subTaskContents = subTaskContents;
            this.coopMode = coopMode;
        }
    }

    /** 子任务分发事件，携带目标 Agent 编码。 */
    @Getter
    @ToString(callSuper = true)
    public static class TaskDispatched extends AgentEvent {
        private final String agentCode;

        public TaskDispatched(String traceId, Long taskId, String agentCode) {
            super("TASK_DISPATCHED", traceId, taskId, LocalDateTime.now());
            this.agentCode = agentCode;
        }
    }

    /** 子任务执行成功事件，携带子任务 ID 和结果摘要。 */
    @Getter
    @ToString(callSuper = true)
    public static class TaskCompleted extends AgentEvent {
        private final Long itemId;
        private final String agentCode;
        private final String resultSummary;

        public TaskCompleted(String traceId, Long taskId, Long itemId, String agentCode, String resultSummary) {
            super("TASK_COMPLETED", traceId, taskId, LocalDateTime.now());
            this.itemId = itemId;
            this.agentCode = agentCode;
            this.resultSummary = resultSummary;
        }
    }

    /** 子任务执行失败事件，携带错误信息。 */
    @Getter
    @ToString(callSuper = true)
    public static class TaskFailed extends AgentEvent {
        private final Long itemId;
        private final String agentCode;
        private final String errorMsg;

        public TaskFailed(String traceId, Long taskId, Long itemId, String agentCode, String errorMsg) {
            super("TASK_FAILED", traceId, taskId, LocalDateTime.now());
            this.itemId = itemId;
            this.agentCode = agentCode;
            this.errorMsg = errorMsg;
        }
    }

    /** 全局上下文更新事件，携带变更的 key。 */
    @Getter
    @ToString(callSuper = true)
    public static class ContextUpdated extends AgentEvent {
        private final String key;

        public ContextUpdated(String traceId, Long taskId, String key) {
            super("CONTEXT_UPDATED", traceId, taskId, LocalDateTime.now());
            this.key = key;
        }
    }

    /** 需要人工审批事件，携带子任务 ID 和 Agent 编码。 */
    @Getter
    @ToString(callSuper = true)
    public static class ApprovalRequired extends AgentEvent {
        private final Long itemId;
        private final String agentCode;

        public ApprovalRequired(String traceId, Long taskId, Long itemId, String agentCode) {
            super("APPROVAL_REQUIRED", traceId, taskId, LocalDateTime.now());
            this.itemId = itemId;
            this.agentCode = agentCode;
        }
    }

    /** 审批完成事件，携带审批结果（通过/驳回）。 */
    @Getter
    @ToString(callSuper = true)
    public static class ApprovalResolved extends AgentEvent {
        private final Long itemId;
        private final boolean approved;

        public ApprovalResolved(String traceId, Long taskId, Long itemId, boolean approved) {
            super("APPROVAL_RESOLVED", traceId, taskId, LocalDateTime.now());
            this.itemId = itemId;
            this.approved = approved;
        }
    }
}
