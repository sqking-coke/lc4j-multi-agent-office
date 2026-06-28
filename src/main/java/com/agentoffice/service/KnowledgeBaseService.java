package com.agentoffice.service;

import com.agentoffice.entity.KbDocument;
import com.agentoffice.mapper.KbDocumentMapper;
import com.agentoffice.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库服务：文档的 CRUD 操作，支持租户隔离和逻辑删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KbDocumentMapper kbDocumentMapper;

    public Page<KbDocument> list(int page, int size) {
        Page<KbDocument> p = new Page<>(page, size);
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getStatus, 1);
        wrapper.orderByDesc(KbDocument::getCreateTime);
        kbDocumentMapper.selectPage(p, wrapper);
        return p;
    }

    @Transactional
    public KbDocument create(KbDocument doc) {
        doc.setTenantId(TenantContext.getTenantId());
        doc.setStatus(1);
        kbDocumentMapper.insert(doc);
        log.info("Knowledge base document created: {}", doc.getDocName());
        return doc;
    }

    @Transactional
    public void update(Long docId, KbDocument update) {
        KbDocument doc = kbDocumentMapper.selectById(docId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        if (update.getDocName() != null) doc.setDocName(update.getDocName());
        if (update.getDocContent() != null) doc.setDocContent(update.getDocContent());
        if (update.getDocType() != null) doc.setDocType(update.getDocType());
        if (update.getAgentCode() != null) doc.setAgentCode(update.getAgentCode());
        kbDocumentMapper.updateById(doc);
    }

    @Transactional
    public void delete(Long docId) {
        KbDocument doc = kbDocumentMapper.selectById(docId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        doc.setStatus(0);
        kbDocumentMapper.updateById(doc);
    }
}
