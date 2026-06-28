package com.agentoffice.lc4j.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import static org.bsc.langgraph4j.StateGraph.END;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 多 Agent 协同办公主 StateGraph — 替代手写版 DispatchHub (230行) + 7 Strategy (660行)。
 *
 * <pre>
 * 图结构: decompose → [routeByCoopMode] → fanout/agent_executor → aggregator
 *
 * PARALLEL   → decompose → fanout → agent_executor → aggregator
 * SERIAL     → decompose → agent_executor → aggregator
 * LOOP       → decompose → agent_executor → [routeForLoop] → agent_executor(↩) | aggregator
 * CONDITION  → decompose → condition_check → agent_executor → aggregator
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfficeStateGraph {

    private final GraphNodes graphNodes;
    private final ExecutorService agentExecutor;

    private CompiledGraph<OfficeAgentState> compiledGraph;

    public synchronized CompiledGraph<OfficeAgentState> getGraph() {
        if (compiledGraph == null) {
            compiledGraph = buildGraph();
        }
        return compiledGraph;
    }

    private CompiledGraph<OfficeAgentState> buildGraph() {
        log.info("Building OfficeStateGraph...");

        try {
            var graph = new StateGraph<>(OfficeAgentState.schema(), OfficeAgentState::new);

            // 5 个节点
            graph.addNode("decompose", AsyncNodeAction.node_async(graphNodes.decomposeNode()));
            graph.addNode("fanout", AsyncNodeAction.node_async(graphNodes.fanoutNode()));
            graph.addNode("agent_executor", AsyncNodeAction.node_async(graphNodes.executeAgentNode()));
            graph.addNode("condition_check", AsyncNodeAction.node_async(state -> new LinkedHashMap<>()));
            graph.addNode("aggregator", AsyncNodeAction.node_async(graphNodes.aggregateNode()));

            // 入口
            graph.setEntryPoint("decompose");

            // decompose → 按协同模式路由
            graph.addConditionalEdges("decompose",
                    AsyncEdgeAction.edge_async(RouteFunctions.routeByCoopMode()),
                    new LinkedHashMap<>(Map.of(
                        "fanout", "fanout",
                        "agent_executor", "agent_executor",
                        "condition_check", "condition_check")));

            // fanout → agent_executor
            graph.addEdge("fanout", "agent_executor");

            // agent_executor → 循环判定或聚合
            graph.addConditionalEdges("agent_executor",
                    AsyncEdgeAction.edge_async(RouteFunctions.routeForLoop()),
                    new LinkedHashMap<>(Map.of(
                        "agent_executor", "agent_executor",
                        "aggregator", "aggregator")));

            // condition_check → agent_executor
            graph.addEdge("condition_check", "agent_executor");

            // aggregator → END
            graph.addEdge("aggregator", END);

            CompiledGraph<OfficeAgentState> compiled = graph.compile(CompileConfig.builder().build());
            log.info("OfficeStateGraph compiled successfully");
            return compiled;
        } catch (GraphStateException e) {
            log.error("Failed to build StateGraph: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to build OfficeStateGraph", e);
        }
    }

    /**
     * 提交协同任务 — 替代 DispatchHub.submitTask()。
     */
    public CompletableFuture<Optional<OfficeAgentState>> submitTask(
            String taskName, String taskContent, Long submitUserId, Long tenantId) {

        String traceId = UUID.randomUUID().toString().substring(0, 8);

        // 构造纯 mutable HashMap 作为 invoke 输入，绕开 AgentState.data() 可能返回 unmodifiable map 的问题
        Map<String, Object> input = new LinkedHashMap<>();
        input.put(OfficeAgentState.KEY_TASK_NAME, taskName);
        input.put(OfficeAgentState.KEY_TASK_CONTENT, taskContent);
        input.put(OfficeAgentState.KEY_SUBMIT_USER_ID, submitUserId);
        input.put(OfficeAgentState.KEY_TENANT_ID, tenantId);
        input.put(OfficeAgentState.KEY_TRACE_ID, traceId);
        input.put(OfficeAgentState.KEY_LOOP_COUNT, 0);
        input.put(OfficeAgentState.KEY_MAX_LOOP, 5);
        input.put(OfficeAgentState.KEY_QUALITY_SCORE, 0.0);
        input.put(OfficeAgentState.KEY_AGENT_RESULTS, new ArrayList<>());
        input.put(OfficeAgentState.KEY_SUB_TASKS, new ArrayList<>());

        return CompletableFuture.supplyAsync(() -> {
            try {
                CompiledGraph<OfficeAgentState> graph = getGraph();
                Optional<OfficeAgentState> result = graph.invoke(input);
                result.ifPresent(s ->
                    log.info("[{}] StateGraph complete: finalStatus={}", traceId,
                            s.data().get(OfficeAgentState.KEY_FINAL_STATUS)));
                return result;
            } catch (Exception e) {
                log.error("[{}] StateGraph failed: {}", traceId, e.getMessage(), e);
                String detail = e.getMessage();
                Throwable cause = e.getCause();
                while (cause != null && cause != e) {
                    detail = cause.toString();
                    cause = cause.getCause();
                }
                input.put(OfficeAgentState.KEY_FINAL_STATUS, 2);
                input.put(OfficeAgentState.KEY_FINAL_SUMMARY, "Error: " + detail);
                return Optional.of(new OfficeAgentState(input));
            }
        }, agentExecutor);
    }
}
