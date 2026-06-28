package com.agentoffice.controller;

import com.agentoffice.entity.AgentTask;
import com.agentoffice.entity.AgentTaskItem;
import com.agentoffice.security.TenantContext;
import com.agentoffice.service.TaskService;
import com.agentoffice.util.ApiResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "任务管理", description = "协同任务的提交、查询、取消和审批")
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "提交协同任务", description = "通过 StateGraph 编排多Agent协同执行")
    @PostMapping
    public ApiResult<AgentTask> submit(@RequestBody TaskSubmitRequest request) {
        Long userId = TenantContext.getUserId();
        Long tenantId = TenantContext.getTenantId();
        if (userId == null) return ApiResult.error("未登录");
        AgentTask task = taskService.submit(
                request.getTaskName(), request.getTaskContent(), userId, tenantId);
        return ApiResult.success(task);
    }

    @Operation(summary = "查询任务列表")
    @GetMapping
    public ApiResult<Page<AgentTask>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "任务状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "协同模式") @RequestParam(required = false) String coopMode) {
        return ApiResult.success(taskService.list(page, size, status, coopMode));
    }

    @Operation(summary = "查询任务详情")
    @GetMapping("/{taskId}")
    public ApiResult<TaskDetail> detail(@PathVariable Long taskId) {
        AgentTask task = taskService.getTask(taskId);
        if (task == null) return ApiResult.error("任务不存在");
        var items = taskService.getItems(taskId);
        TaskDetail detail = new TaskDetail();
        detail.setTask(task);
        detail.setItems(items);
        return ApiResult.success(detail);
    }

    @Operation(summary = "取消任务")
    @DeleteMapping("/{taskId}")
    public ApiResult<Void> cancel(@PathVariable Long taskId) {
        taskService.cancel(taskId);
        return ApiResult.success();
    }

    @Operation(summary = "审批通过")
    @PostMapping("/{taskItemId}/approve")
    public ApiResult<String> approve(@PathVariable Long taskItemId) {
        try {
            taskService.approveItem(taskItemId);
            return ApiResult.success("审批通过");
        } catch (IllegalArgumentException e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @Operation(summary = "审批驳回")
    @PostMapping("/{taskItemId}/reject")
    public ApiResult<String> reject(
            @PathVariable Long taskItemId,
            @RequestBody(required = false) RejectRequest request) {
        try {
            taskService.rejectItem(taskItemId, request != null ? request.getReason() : "人工驳回");
            return ApiResult.success("已驳回");
        } catch (IllegalArgumentException e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @Data
    @Schema(description = "任务提交请求")
    public static class TaskSubmitRequest {
        @Schema(description = "任务名称") private String taskName;
        @Schema(description = "任务内容") private String taskContent;
    }

    @Data
    @Schema(description = "任务详情")
    public static class TaskDetail {
        @Schema(description = "主任务") private AgentTask task;
        @Schema(description = "子任务列表") private List<AgentTaskItem> items;
    }

    @Data
    @Schema(description = "驳回请求")
    public static class RejectRequest {
        @Schema(description = "驳回原因") private String reason;
    }
}
