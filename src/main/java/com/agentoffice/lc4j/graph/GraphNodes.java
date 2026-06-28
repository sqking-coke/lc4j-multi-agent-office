package com.agentoffice.lc4j.graph;

import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.lc4j.graph.OfficeAgentState.SubTask;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * LangGraph4j Node 实现集合。
 *
 * <p>executeAgentNode 按依赖拓扑分层并行执行子任务：
 * 同层无依赖的任务并发调用 LLM，跨层串行等待。</p>
 */
@Slf4j
@Component
public class GraphNodes {

    private final AgentRegistry agentRegistry;
    private final ExecutorService agentExecutor;

    public GraphNodes(AgentRegistry agentRegistry, ExecutorService agentExecutor) {
        this.agentRegistry = agentRegistry;
        this.agentExecutor = agentExecutor;
    }

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
        if (combined.contains("翻译") || combined.contains("translate")
                || combined.contains("国际化") || combined.contains("i18n")
                || combined.contains("多语言")) {
            return List.of(new SubTask("翻译处理", taskContent, "translation", 1, List.of()));
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

    /**
     * 按依赖拓扑分层并行执行子任务。
     *
     * <p>算法：
     * <ol>
     *   <li>将子任务按 {@code dependsOn} 分成多个层级——层级 0 无依赖，层级 N 依赖的任务都在 N-1 层已完成</li>
     *   <li>同一层级内的所有子任务通过 {@link CompletableFuture} 并行执行</li>
     *   <li>当前层级全部完成后才进入下一层级</li>
     * </ol>
     *
     * <p>效果：独立子任务并行（如工单分析的 2 个无依赖子任务从 5s 降到 2.5s），
     * 有依赖的子任务仍保证执行顺序。</p>
     */
    public NodeAction<OfficeAgentState> executeAgentNode() {
        return state -> {
            var subTasks = state.getSubTasks();
            String traceId = state.getTraceId();

            // Build dependency levels
            List<List<SubTask>> levels = buildDependencyLevels(subTasks);
            log.info("[{}] Executing {} sub-tasks in {} level(s)", traceId, subTasks.size(), levels.size());

            // Preserve original order for result placement
            Map<Integer, Map<String, Object>> resultByOrder = new LinkedHashMap<>();

            for (int levelIdx = 0; levelIdx < levels.size(); levelIdx++) {
                List<SubTask> level = levels.get(levelIdx);
                if (level.size() == 1) {
                    // Single task — run inline, no overhead
                    SubTask task = level.get(0);
                    Map<String, Object> entry = runOne(task, traceId, state.getTaskName(), state.getTenantId());
                    resultByOrder.put(task.order(), entry);
                } else {
                    // Multiple tasks — parallel
                    log.info("[{}] Level {}: running {} sub-tasks in parallel", traceId, levelIdx, level.size());
                    @SuppressWarnings("unchecked")
                    CompletableFuture<Map.Entry<Integer, Map<String, Object>>>[] futures = level.stream()
                        .map(task -> CompletableFuture.supplyAsync(() -> {
                            Map<String, Object> entry = runOne(task, traceId, state.getTaskName(), state.getTenantId());
                            return new AbstractMap.SimpleEntry<>(task.order(), entry);
                        }, agentExecutor))
                        .toArray(CompletableFuture[]::new);
                    CompletableFuture.allOf(futures).join();
                    for (var f : futures) {
                        var entry = f.getNow(null);
                        if (entry != null) resultByOrder.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            // Reconstruct results in original order
            List<Map<String, Object>> agentResults = new ArrayList<>();
            subTasks.stream().sorted(Comparator.comparingInt(SubTask::order))
                    .forEach(t -> {
                        Map<String, Object> r = resultByOrder.get(t.order());
                        if (r != null) agentResults.add(r);
                    });

            Map<String, Object> result = new LinkedHashMap<>();
            result.put(OfficeAgentState.KEY_AGENT_RESULTS, agentResults);
            result.put(OfficeAgentState.KEY_LOOP_COUNT, state.getLoopCount() + 1);
            result.put(OfficeAgentState.KEY_QUALITY_SCORE, 1.0);
            return result;
        };
    }

    /** Execute a single sub-task and return a result entry. */
    private Map<String, Object> runOne(SubTask task, String traceId, String taskName, Long tenantId) {
        Optional<BizAgent> agentOpt = agentRegistry.findBestForCapability(task.requiredCapability());
        if (agentOpt.isEmpty()) {
            log.warn("[{}] No agent for capability: {}", traceId, task.requiredCapability());
            Map<String, Object> skip = new LinkedHashMap<>();
            skip.put("agentCode", "none");
            skip.put("taskName", task.name());
            skip.put("success", false);
            skip.put("summary", "No agent for: " + task.requiredCapability());
            skip.put("costTimeMs", 0);
            return skip;
        }
        BizAgent agent = agentOpt.get();
        log.info("[{}] Executing {} for sub-task: {}", traceId, agent.getAgentCode(), task.name());

        try {
            long start = System.currentTimeMillis();
            BizAgent.AgentResult agentResult = agent.execute(
                    UUID.randomUUID().toString(), task.content(),
                    Map.of("traceId", traceId, "taskName", taskName, "tenantId", tenantId));
            long cost = System.currentTimeMillis() - start;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("agentCode", agent.getAgentCode());
            entry.put("taskName", task.name());
            entry.put("success", agentResult.isSuccess());
            entry.put("summary", agentResult.getSummary());
            entry.put("costTimeMs", cost);
            entry.put("data", agentResult.getData());
            if (!agentResult.isSuccess() && agentResult.getErrorMsg() != null) {
                entry.put("error", agentResult.getErrorMsg());
            }
            return entry;
        } catch (Exception e) {
            log.error("[{}] Agent {} failed: {}", traceId, agent.getAgentCode(), e.getMessage());
            Map<String, Object> errEntry = new LinkedHashMap<>();
            errEntry.put("agentCode", agent.getAgentCode());
            errEntry.put("taskName", task.name());
            errEntry.put("success", false);
            errEntry.put("summary", "Error: " + e.getMessage());
            errEntry.put("costTimeMs", 0);
            return errEntry;
        }
    }

    /**
     * Topological grouping by dependency depth.
     * Level 0: tasks with no dependencies.
     * Level N: tasks whose all dependencies are resolved by levels < N.
     */
    private List<List<SubTask>> buildDependencyLevels(List<SubTask> tasks) {
        List<List<SubTask>> levels = new ArrayList<>();
        Set<Integer> resolved = new HashSet<>();
        List<SubTask> remaining = new ArrayList<>(tasks);

        while (!remaining.isEmpty()) {
            List<SubTask> currentLevel = new ArrayList<>();
            List<SubTask> stillWaiting = new ArrayList<>();

            for (SubTask t : remaining) {
                if (resolved.containsAll(t.dependsOn())) {
                    currentLevel.add(t);
                } else {
                    stillWaiting.add(t);
                }
            }

            if (currentLevel.isEmpty()) {
                // Circular dependency or broken reference — dump all as one parallel level
                log.warn("Circular/broken dependency detected, running {} remaining tasks in one level", remaining.size());
                levels.add(new ArrayList<>(remaining));
                break;
            }

            levels.add(currentLevel);
            currentLevel.forEach(t -> resolved.add(t.order()));
            remaining = stillWaiting;
        }
        return levels;
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
