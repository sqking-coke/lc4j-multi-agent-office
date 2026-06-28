package com.agentoffice.lc4j.controller;

import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.lc4j.graph.OfficeAgentState;
import com.agentoffice.lc4j.graph.OfficeStateGraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务编排控制器 — 通过 LangGraph4j StateGraph 提交复杂协同任务。
 *
 * <p>替代手写版 TaskController 中调用 DispatchHub.submitTask() 的逻辑。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/tasks")
@RequiredArgsConstructor
public class TaskGraphController {

    private final OfficeStateGraph stateGraph;
    private final AgentRegistry agentRegistry;

    @PostMapping("/submit")
    public Map<String, Object> submitTask(@RequestBody TaskSubmitRequest request) {
        log.info("Submitted task via StateGraph: {}", request.taskName());
        try {
            var future = stateGraph.submitTask(
                    request.taskName(), request.taskContent(),
                    request.submitUserId(), request.tenantId());
            var stateOpt = future.get(); // blocking for simplicity
            if (stateOpt.isPresent()) {
                var state = stateOpt.get();
                Map<String, Object> result = new HashMap<>();
                result.put("traceId", state.getTraceId());
                result.put("taskName", state.getTaskName());
                result.put("coopMode", state.getCoopMode());
                result.put("finalStatus", state.data().get(OfficeAgentState.KEY_FINAL_STATUS));
                result.put("finalSummary", state.data().get(OfficeAgentState.KEY_FINAL_SUMMARY));
                result.put("agentResults", state.getAgentResults());
                result.put("success", true);
                return result;
            }
            return Map.of("success", false, "error", "No result returned");
        } catch (Exception e) {
            log.error("Task execution failed", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @GetMapping("/agents")
    public Map<String, Object> listAgents() {
        var agents = agentRegistry.getAllAgents();
        Map<String, Object> result = new HashMap<>();
        result.put("total", agents.size());
        result.put("enabled", agentRegistry.getEnabledCount());
        result.put("agents", agents.stream().map(a -> Map.of(
                "code", a.getAgentCode(),
                "capabilities", a.getCapability().getCapabilities(),
                "healthy", a.healthCheck()
        )).toList());
        result.put("success", true);
        return result;
    }

    public record TaskSubmitRequest(
            String taskName, String taskContent,
            Long submitUserId, Long tenantId
    ) {}
}
