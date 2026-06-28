package com.agentoffice.controller;

import com.agentoffice.agent.*;
import com.agentoffice.agent.registry.*;
import com.agentoffice.entity.*;
import com.agentoffice.service.*;
import com.agentoffice.util.*;
import com.alibaba.fastjson2.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.tags.*;
import lombok.*;
import org.springframework.security.access.prepost.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "Agent管理", description = "智能体的注册、查询、启停和指标监控")
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @Operation(summary = "查询Agent列表", description = "获取所有已启用的智能体列表")
    @GetMapping
    public ApiResult<List<AgentInfo>> list() {
        return ApiResult.success(agentService.listEnabled());
    }

    @Operation(summary = "查询Agent详情", description = "根据Agent编码获取详细信息")
    @GetMapping("/{agentCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<AgentInfo> detail(
            @Parameter(description = "Agent唯一编码", example = "ticket_agent") @PathVariable String agentCode) {
        AgentInfo info = agentService.getByAgentCode(agentCode);
        return info != null ? ApiResult.success(info) : ApiResult.error("Agent不存在");
    }

    @Operation(summary = "注册Agent", description = "注册一个新的智能体到系统中")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<String> register(@RequestBody AgentRegisterRequest request) {
        try {
            AgentInfo info = new AgentInfo();
            info.setAgentCode(request.getAgentCode());
            info.setAgentName(request.getAgentName());
            info.setAgentCapability(JSON.toJSONString(request.getAgentCapability()));
            info.setConfigJson(request.getConfigJson());
            info.setPriority(request.getPriority() != null ? request.getPriority() : 100);
            info.setMaxConcurrency(request.getMaxConcurrency() != null ? request.getMaxConcurrency() : 3);
            agentService.register(info);
            return ApiResult.success("注册成功");
        } catch (IllegalArgumentException e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @Operation(summary = "更新Agent配置", description = "修改指定Agent的属性信息")
    @PutMapping("/{agentCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<String> update(
            @Parameter(description = "Agent唯一编码") @PathVariable String agentCode,
            @RequestBody AgentRegisterRequest request) {
        try {
            AgentInfo update = new AgentInfo();
            update.setAgentName(request.getAgentName());
            if (request.getAgentCapability() != null) update.setAgentCapability(JSON.toJSONString(request.getAgentCapability()));
            update.setConfigJson(request.getConfigJson());
            update.setPriority(request.getPriority());
            update.setMaxConcurrency(request.getMaxConcurrency());
            agentService.update(agentCode, update);
            return ApiResult.success("更新成功");
        } catch (IllegalArgumentException e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @Operation(summary = "注销Agent", description = "从系统中移除指定的Agent")
    @DeleteMapping("/{agentCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<String> unregister(
            @Parameter(description = "Agent唯一编码") @PathVariable String agentCode) {
        agentService.unregister(agentCode);
        return ApiResult.success("注销成功");
    }

    @Operation(summary = "切换Agent状态", description = "启用或禁用指定的Agent")
    @PatchMapping("/{agentCode}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<String> toggleStatus(
            @Parameter(description = "Agent唯一编码") @PathVariable String agentCode,
            @Parameter(description = "status: 1启用 0禁用") @RequestBody Map<String, Integer> body) {
        try {
            agentService.toggleStatus(agentCode, body.get("status"));
            return ApiResult.success(body.get("status") == 1 ? "已启用" : "已禁用");
        } catch (IllegalArgumentException e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @Operation(summary = "Agent运行指标", description = "获取所有Agent的运行指标数据")
    @GetMapping("/metrics")
    public ApiResult<List<Map<String, Object>>> metrics() {
        return ApiResult.success(agentService.getMetrics());
    }

    @Data
    @Schema(description = "Agent注册/更新请求")
    public static class AgentRegisterRequest {
        @Schema(description = "Agent唯一编码", example = "custom_agent")
        private String agentCode;
        @Schema(description = "Agent名称", example = "自定义Agent")
        private String agentName;
        @Schema(description = "Agent能力配置")
        private BizAgent.AgentCapability agentCapability;
        @Schema(description = "Agent配置JSON(模型/温度/maxTokens等)")
        private String configJson;
        @Schema(description = "执行优先级，数值越小越高", example = "100")
        private Integer priority;
        @Schema(description = "最大并发执行数", example = "3")
        private Integer maxConcurrency;
    }
}
