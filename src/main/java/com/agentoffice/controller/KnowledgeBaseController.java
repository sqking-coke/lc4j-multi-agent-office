package com.agentoffice.controller;

import com.agentoffice.entity.KbDocument;
import com.agentoffice.service.KnowledgeBaseService;
import com.agentoffice.util.ApiResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "知识库管理", description = "企业知识库文档的CRUD操作，支持按Agent隔离")
@RestController
@RequestMapping("/api/v1/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    @Operation(summary = "查询文档列表", description = "分页查询知识库文档列表")
    @GetMapping
    public ApiResult<Page<KbDocument>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(kbService.list(page, size));
    }

    @Operation(summary = "新增文档", description = "向知识库中添加一篇文档，可指定关联的Agent编码(留空则为全局共享)")
    @PostMapping
    public ApiResult<String> create(@RequestBody KbDocRequest request) {
        KbDocument doc = new KbDocument();
        doc.setDocName(request.getDocName());
        doc.setDocContent(request.getDocContent());
        doc.setDocType(request.getDocType());
        doc.setAgentCode(request.getAgentCode());
        kbService.create(doc);
        return ApiResult.success("创建成功");
    }

    @Operation(summary = "更新文档", description = "修改指定文档的内容或属性")
    @PutMapping("/{docId}")
    public ApiResult<String> update(
            @Parameter(description = "文档ID") @PathVariable Long docId,
            @RequestBody KbDocRequest request) {
        try {
            KbDocument update = new KbDocument();
            update.setDocName(request.getDocName());
            update.setDocContent(request.getDocContent());
            update.setDocType(request.getDocType());
            update.setAgentCode(request.getAgentCode());
            kbService.update(docId, update);
            return ApiResult.success("更新成功");
        } catch (IllegalArgumentException e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @Operation(summary = "删除文档", description = "从知识库中移除指定文档")
    @DeleteMapping("/{docId}")
    public ApiResult<String> delete(
            @Parameter(description = "文档ID") @PathVariable Long docId) {
        try {
            kbService.delete(docId);
            return ApiResult.success("已删除");
        } catch (IllegalArgumentException e) {
            return ApiResult.error(e.getMessage());
        }
    }

    @Data
    @Schema(description = "知识库文档请求")
    public static class KbDocRequest {
        @Schema(description = "文档名称", example = "员工手册")
        private String docName;
        @Schema(description = "文档内容", example = "## 第一章 总则...")
        private String docContent;
        @Schema(description = "文档类型: manual/policy/faq/api_doc", example = "manual")
        private String docType;
        @Schema(description = "关联Agent编码，NULL为全局共享", example = "rag_qa_agent")
        private String agentCode;
    }
}
