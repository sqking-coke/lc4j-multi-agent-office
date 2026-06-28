package com.agentoffice.controller;

import com.agentoffice.entity.AgentPromptTemplate;
import com.agentoffice.service.PromptService;
import com.agentoffice.util.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Prompt模板管理", description = "Agent Prompt模板的创建、启停和版本回滚(仅管理员)")
@RestController
@RequestMapping("/api/v1/prompts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PromptController {

    private final PromptService promptService;

    @Operation(summary = "查询模板列表", description = "获取Prompt模板列表，可按Agent筛选")
    @GetMapping
    public ApiResult<List<AgentPromptTemplate>> list(
            @Parameter(description = "Agent编码，不传则查全部") @RequestParam(required = false) String agentCode) {
        return ApiResult.success(promptService.listByAgentCode(agentCode));
    }

    @Operation(summary = "查询模板详情", description = "根据模板ID获取详细信息")
    @GetMapping("/{templateId}")
    public ApiResult<AgentPromptTemplate> detail(
            @Parameter(description = "模板ID") @PathVariable Long templateId) {
        return ApiResult.success(promptService.getById(templateId));
    }

    @Operation(summary = "创建Prompt模板", description = "为指定Agent创建新的Prompt模板，支持 {{变量}} 占位符")
    @PostMapping
    public ApiResult<String> create(@RequestBody PromptCreateRequest request) {
        AgentPromptTemplate template = new AgentPromptTemplate();
        template.setTemplateCode(request.getTemplateCode());
        template.setTemplateContent(request.getTemplateContent());
        template.setVersion(request.getVersion());
        template.setAgentCode(request.getAgentCode());
        promptService.create(template);
        return ApiResult.success("创建成功，ID: " + template.getId());
    }

    @Operation(summary = "启用模板", description = "激活指定版本的模板，同时停用该Agent的其他版本")
    @PutMapping("/{templateId}/activate")
    public ApiResult<String> activate(
            @Parameter(description = "模板ID") @PathVariable Long templateId) {
        try {
            promptService.activate(templateId);
            return ApiResult.success("已启用");
        } catch (IllegalArgumentException e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @Operation(summary = "回滚模板", description = "回滚到指定历史版本，停用当前版本并激活目标版本")
    @PostMapping("/{templateId}/rollback")
    public ApiResult<String> rollback(
            @Parameter(description = "目标模板ID") @PathVariable Long templateId) {
        try {
            promptService.rollback(templateId);
            return ApiResult.success("已回滚");
        } catch (IllegalArgumentException e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @Data
    @Schema(description = "Prompt创建请求")
    public static class PromptCreateRequest {
        @Schema(description = "模板编码", example = "ticket_prompt_v1")
        private String templateCode;
        @Schema(description = "模板内容(支持{{变量}}占位符)", example = "你是一个{{role}}，请处理以下内容...")
        private String templateContent;
        @Schema(description = "版本号", example = "v1")
        private String version;
        @Schema(description = "绑定的Agent编码", example = "ticket_agent")
        private String agentCode;
    }
}
