package com.agentoffice.lc4j.graph;

import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.lc4j.graph.OfficeAgentState.SubTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * LangGraph4j Node 实现集合 —— 替代手写版 DispatchHub.submitTask()（230行）。
 *
 * <p>每个 Node 实现 {@link NodeAction}，接收 AgentState，返回部分更新的 Map。
 * LangGraph4j 自动通过 Channel Reducer 合并更新。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphNodes {

    private final AgentRegistry agentRegistry;

    // ==================== Decompose Node ====================

    public NodeAction<OfficeAgentState> decomposeNode() {
        return state -> {
            String taskName = state.getTaskName();
            String taskContent = state.getTaskContent();
            String traceId = state.getTraceId();

            log.info("[{}] Decomposing task: {}", traceId, taskName);

            List<SubTask> subTasks = decomposeByKeyword(taskName, taskContent);
            String coopMode = inferCoopMode(subTasks);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put(OfficeAgentState.KEY_SUB_TASKS, subTasks);
            result.put(OfficeAgentState.KEY_COOP_MODE, coopMode);
            result.put(OfficeAgentState.KEY_DECOMPOSE_SOURCE, "TEMPLATE");
            result.put(OfficeAgentState.KEY_MAX_LOOP,
                    subTasks.stream().anyMatch(t -> t.name().contains("审查")) ? 5 : 1);

            log.info("[{}] Decomposed into {} sub-tasks, coopMode={}", traceId, subTasks.size(), coopMode);
            return result;
        };
    }

    private List<SubTask> decomposeByKeyword(String taskName, String taskContent) {
        String combined = (taskName + " " + taskContent).toLowerCase();

        if (combined.contains("周报") || combined.contains("月报") || combined.contains("数据")) {
            return List.of(
                new SubTask("数据提取", taskContent, "data_report", 1, List.of()),
                new SubTask("报告生成", taskContent, "data_report", 2, List.of(1)),
                new SubTask("报告审查", taskContent, "code_review", 3, List.of(2)));
        }
        if (combined.contains("代码") || combined.contains("审查") || combined.contains("review")) {
            return List.of(
                new SubTask("代码审查", taskContent, "code_review", 1, List.of()),
                new SubTask("文档总结", taskContent, "doc_analysis", 2, List.of(1)));
        }
        if (combined.contains("工单") || combined.contains("ticket")) {
            return List.of(
                new SubTask("工单分析", taskContent, "ticket_process", 1, List.of()),
                new SubTask("知识检索", taskContent, "rag_qa", 2, List.of()));
        }
        if (combined.contains("文档") || combined.contains("总结") || combined.contains("改写")) {
            return List.of(new SubTask("文档处理", taskContent, "doc_analysis", 1, List.of()));
        }
        if (combined.contains("问答") || combined.contains("答疑")) {
            return List.of(
                new SubTask("知识检索", taskContent, "rag_qa", 1, List.of()),
                new SubTask("文档补充", taskContent, "doc_analysis", 2, List.of()));
        }
        return List.of(new SubTask(taskName, taskContent, "rag_qa", 1, List.of()));
    }

    private String inferCoopMode(List<SubTask> subTasks) {
        if (subTasks.size() <= 1) return "SERIAL";
        boolean hasDeps = subTasks.stream().anyMatch(t -> !t.dependsOn().isEmpty());
        return hasDeps ? "SERIAL" : "PARALLEL";
    }

    // ==================== Fan-out Node ====================

    public NodeAction<OfficeAgentState> fanoutNode() {
        return state -> {
            log.info("[{}] Fan-out: {} sub-tasks ready", state.getTraceId(), state.getSubTasks().size());
            return new LinkedHashMap<>();
        };
    }

    // ==================== Agent Execution Node ====================

    public NodeAction<OfficeAgentState> executeAgentNode() {
        return state -> {
            var subTasks = state.getSubTasks();
            String traceId = state.getTraceId();
            List<Map<String, Object>> agentResults = new ArrayList<>();

            for (SubTask task : subTasks) {
                Optional<BizAgent> agentOpt = agentRegistry.findBestForCapability(task.requiredCapability());
                if (agentOpt.isEmpty()) {
                    log.warn("[{}] No agent for capability: {}", traceId, task.requiredCapability());
                    continue;
                }
                BizAgent agent = agentOpt.get();
                log.info("[{}] Executing {} for sub-task: {}", traceId, agent.getAgentCode(), task.name());

                try {
                    long start = System.currentTimeMillis();
                    BizAgent.AgentResult agentResult = agent.execute(
                            UUID.randomUUID().toString(), task.content(),
                            Map.of("traceId", traceId, "taskName", state.getTaskName(), "tenantId", state.getTenantId()));
                    long cost = System.currentTimeMillis() - start;

                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("agentCode", agent.getAgentCode());
                    entry.put("taskName", task.name());
                    entry.put("success", agentResult.isSuccess());
                    entry.put("summary", agentResult.getSummary());
                    entry.put("costTimeMs", cost);
                    entry.put("data", agentResult.getData());
                    agentResults.add(entry);
                } catch (Exception e) {
                    log.error("[{}] Agent {} failed: {}", traceId, agent.getAgentCode(), e.getMessage());
                    Map<String, Object> errEntry = new LinkedHashMap<>();
                    errEntry.put("agentCode", agent.getAgentCode());
                    errEntry.put("taskName", task.name());
                    errEntry.put("success", false);
                    errEntry.put("summary", "Error: " + e.getMessage());
                    errEntry.put("costTimeMs", 0);
                    agentResults.add(errEntry);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put(OfficeAgentState.KEY_AGENT_RESULTS, agentResults);
            result.put(OfficeAgentState.KEY_LOOP_COUNT, state.getLoopCount() + 1);  // 递增
            result.put(OfficeAgentState.KEY_QUALITY_SCORE, 1.0);  // 单次执行即达标，退出循环
            return result;
        };
    }

    // ==================== Aggregate Node ====================

    public NodeAction<OfficeAgentState> aggregateNode() {
        return state -> {
            var results = state.getAgentResults();
            long successCount = results.stream().filter(r -> (boolean) r.getOrDefault("success", false)).count();

            StringBuilder report = new StringBuilder();
            report.append("## ").append(state.getTaskName()).append(" - 协同执行报告\n\n");
            report.append("**协同模式**: ").append(state.getCoopMode()).append("\n");
            report.append("**执行结果**: ").append(successCount).append("/").append(results.size()).append(" 成功\n\n");

            for (int i = 0; i < results.size(); i++) {
                var r = results.get(i);
                report.append("### ").append(i + 1).append(". ").append(r.get("taskName")).append("\n");
                report.append("- Agent: ").append(r.get("agentCode")).append("\n");
                report.append("- 状态: ").append((boolean) r.getOrDefault("success", false) ? "成功" : "失败").append("\n");
                report.append("- 摘要: ").append(r.get("summary")).append("\n");
                report.append("- 耗时: ").append(r.get("costTimeMs")).append("ms\n\n");
            }

            int finalStatus = successCount == results.size() ? 1 : (successCount == 0 ? 2 : 1);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put(OfficeAgentState.KEY_FINAL_SUMMARY, report.toString());
            result.put(OfficeAgentState.KEY_FINAL_STATUS, finalStatus);

            log.info("[{}] Aggregation: {}/{} success, finalStatus={}", state.getTraceId(), successCount, results.size(), finalStatus);
            return result;
        };
    }
}
