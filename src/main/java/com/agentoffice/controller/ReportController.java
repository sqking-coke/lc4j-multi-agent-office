package com.agentoffice.controller;

import com.agentoffice.entity.AgentStatReport;
import com.agentoffice.entity.LlmTokenDailyStat;
import com.agentoffice.service.ReportService;
import com.agentoffice.util.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "报表统计", description = "Agent协同统计报表和Token消耗查询")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "查询统计报表", description = "按报表类型和Agent编码筛选统计报表")
    @GetMapping
    public ApiResult<List<AgentStatReport>> reports(
            @Parameter(description = "报表类型: DAILY/WEEKLY/MONTHLY") @RequestParam(required = false) String type,
            @Parameter(description = "Agent编码") @RequestParam(required = false) String agentCode) {
        return ApiResult.success(reportService.listReports(type, agentCode));
    }

    @Operation(summary = "Token消耗统计", description = "查询LLM Token的每日消耗明细(仅管理员)")
    @GetMapping("/token-usage")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<List<LlmTokenDailyStat>> tokenUsage(
            @Parameter(description = "Agent编码，不传则查全部") @RequestParam(required = false) String agentCode) {
        return ApiResult.success(reportService.getTokenUsage(agentCode));
    }

    @Operation(summary = "Agent绩效统计", description = "查询Agent的任务执行统计数据")
    @GetMapping("/agent-stats")
    public ApiResult<List<AgentStatReport>> agentStats(
            @Parameter(description = "Agent编码") @RequestParam(required = false) String agentCode,
            @Parameter(description = "报表类型") @RequestParam(defaultValue = "MONTHLY") String type) {
        return ApiResult.success(reportService.getAgentStats(agentCode, type));
    }
}
