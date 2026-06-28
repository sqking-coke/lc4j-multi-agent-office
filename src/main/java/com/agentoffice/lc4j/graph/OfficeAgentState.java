package com.agentoffice.lc4j.graph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;

/**
 * 多 Agent 协同办公共享状态 —— 替代手写版 GlobalContext（102行 ConcurrentHashMap + ReentrantReadWriteLock）。
 *
 * <p>基于 LangGraph4j AgentState，通过 Channel Reducer 机制解决并行写入冲突。
 * 每个 StateGraph node 读取/写入此状态，LangGraph4j 自动合并部分更新。</p>
 */
public class OfficeAgentState extends AgentState {

    // ========== Schema Keys ==========
    public static final String KEY_TASK_ID = "taskId";
    public static final String KEY_TASK_NAME = "taskName";
    public static final String KEY_TASK_CONTENT = "taskContent";
    public static final String KEY_TRACE_ID = "traceId";
    public static final String KEY_SUBMIT_USER_ID = "submitUserId";
    public static final String KEY_TENANT_ID = "tenantId";
    public static final String KEY_COOP_MODE = "coopMode";
    public static final String KEY_DECOMPOSE_SOURCE = "decomposeSource";
    public static final String KEY_SUB_TASKS = "subTasks";
    public static final String KEY_AGENT_RESULTS = "agentResults";
    public static final String KEY_LOOP_COUNT = "loopCount";
    public static final String KEY_MAX_LOOP = "maxLoop";
    public static final String KEY_QUALITY_SCORE = "qualityScore";
    public static final String KEY_NEXT_ACTION = "nextAction";
    public static final String KEY_APPROVAL_REQUIRED = "approvalRequired";
    public static final String KEY_APPROVAL_RESULT = "approvalResult";
    public static final String KEY_FINAL_STATUS = "finalStatus";
    public static final String KEY_FINAL_SUMMARY = "finalSummary";

    // ========== Schema ==========
    public static Map<String, Channel<?>> schema() {
        Map<String, Channel<?>> s = new LinkedHashMap<>();
        s.put(KEY_TASK_ID, Channel.<Long>of(() -> 0L));
        s.put(KEY_TASK_NAME, Channel.<String>of(() -> ""));
        s.put(KEY_TASK_CONTENT, Channel.<String>of(() -> ""));
        s.put(KEY_TRACE_ID, Channel.<String>of(() -> ""));
        s.put(KEY_SUBMIT_USER_ID, Channel.<Long>of(() -> 0L));
        s.put(KEY_TENANT_ID, Channel.<Long>of(() -> 0L));
        s.put(KEY_COOP_MODE, Channel.<String>of(() -> ""));
        s.put(KEY_DECOMPOSE_SOURCE, Channel.<String>of(() -> ""));
        s.put(KEY_SUB_TASKS, Channel.<List<SubTask>>of(
                (oldList, newList) -> { oldList.addAll(newList); return oldList; }, ArrayList::new));
        s.put(KEY_AGENT_RESULTS, Channel.<List<Map<String, Object>>>of(
                (oldList, newList) -> { oldList.addAll(newList); return oldList; }, ArrayList::new));
        s.put(KEY_LOOP_COUNT, Channel.<Integer>of(() -> 0));
        s.put(KEY_MAX_LOOP, Channel.<Integer>of(() -> 5));
        s.put(KEY_QUALITY_SCORE, Channel.<Double>of(() -> 0.0));
        s.put(KEY_NEXT_ACTION, Channel.<String>of(() -> ""));
        s.put(KEY_APPROVAL_REQUIRED, Channel.<Boolean>of(() -> false));
        s.put(KEY_APPROVAL_RESULT, Channel.<String>of(() -> ""));
        s.put(KEY_FINAL_STATUS, Channel.<Integer>of(() -> 0));
        s.put(KEY_FINAL_SUMMARY, Channel.<String>of(() -> ""));
        return s;
    }

    // ========== Constructors ==========
    public OfficeAgentState(Map<String, Object> initData) {
        super(initData);
    }

    public static OfficeAgentState create(String taskName, String taskContent,
                                           Long submitUserId, Long tenantId) {
        Map<String, Object> data = new LinkedHashMap<>();
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        data.put(KEY_TRACE_ID, traceId);
        data.put(KEY_TASK_NAME, taskName);
        data.put(KEY_TASK_CONTENT, taskContent);
        data.put(KEY_SUBMIT_USER_ID, submitUserId);
        data.put(KEY_TENANT_ID, tenantId);
        data.put(KEY_LOOP_COUNT, 0);
        data.put(KEY_MAX_LOOP, 5);
        data.put(KEY_QUALITY_SCORE, 0.0);
        data.put(KEY_AGENT_RESULTS, new ArrayList<>());
        data.put(KEY_SUB_TASKS, new ArrayList<>());
        return new OfficeAgentState(data);
    }

    // ========== Convenience Accessors ==========
    public String getTraceId() { return value(KEY_TRACE_ID, ""); }
    public String getTaskName() { return value(KEY_TASK_NAME, ""); }
    public String getTaskContent() { return value(KEY_TASK_CONTENT, ""); }
    public Long getTenantId() { return value(KEY_TENANT_ID, 0L); }
    public String getCoopMode() { return value(KEY_COOP_MODE, "SERIAL"); }
    public int getLoopCount() { return value(KEY_LOOP_COUNT, 0); }
    public int getMaxLoop() { return value(KEY_MAX_LOOP, 5); }
    public double getQualityScore() { return value(KEY_QUALITY_SCORE, 0.0); }
    public String getNextAction() { return value(KEY_NEXT_ACTION, "aggregator"); }

    @SuppressWarnings("unchecked")
    public List<SubTask> getSubTasks() {
        return (List<SubTask>) data().getOrDefault(KEY_SUB_TASKS, List.of());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAgentResults() {
        return (List<Map<String, Object>>) data().getOrDefault(KEY_AGENT_RESULTS, List.of());
    }

    // ========== SubTask Record ==========
    public record SubTask(
        String name, String content, String requiredCapability,
        int order, List<Integer> dependsOn
    ) implements java.io.Serializable {}
}
