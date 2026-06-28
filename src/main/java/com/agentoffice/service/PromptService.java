package com.agentoffice.service;

import com.agentoffice.entity.AgentPromptTemplate;
import com.agentoffice.mapper.AgentPromptTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Prompt 模板服务：模板的创建、版本激活（同时停用旧版本）和历史版本回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

    private final AgentPromptTemplateMapper promptMapper;

    public List<AgentPromptTemplate> listByAgentCode(String agentCode) {
        LambdaQueryWrapper<AgentPromptTemplate> wrapper = new LambdaQueryWrapper<>();
        if (agentCode != null) wrapper.eq(AgentPromptTemplate::getAgentCode, agentCode);
        wrapper.orderByDesc(AgentPromptTemplate::getCreateTime);
        return promptMapper.selectList(wrapper);
    }

    public AgentPromptTemplate getById(Long templateId) {
        return promptMapper.selectById(templateId);
    }

    @Transactional
    public AgentPromptTemplate create(AgentPromptTemplate template) {
        template.setStatus(0); // draft
        promptMapper.insert(template);
        return template;
    }

    @Transactional
    public void activate(Long templateId) {
        AgentPromptTemplate template = promptMapper.selectById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在");
        }

        List<AgentPromptTemplate> actives = promptMapper.selectList(
                new LambdaQueryWrapper<AgentPromptTemplate>()
                        .eq(AgentPromptTemplate::getAgentCode, template.getAgentCode())
                        .eq(AgentPromptTemplate::getStatus, 1));
        for (AgentPromptTemplate active : actives) {
            active.setStatus(2); // deprecated
            promptMapper.updateById(active);
        }

        template.setStatus(1);
        promptMapper.updateById(template);
        log.info("Activated prompt template {} version {} for agent {}",
                templateId, template.getVersion(), template.getAgentCode());
    }

    @Transactional
    public void rollback(Long templateId) {
        AgentPromptTemplate target = promptMapper.selectById(templateId);
        if (target == null) {
            throw new IllegalArgumentException("目标版本不存在");
        }

        List<AgentPromptTemplate> actives = promptMapper.selectList(
                new LambdaQueryWrapper<AgentPromptTemplate>()
                        .eq(AgentPromptTemplate::getAgentCode, target.getAgentCode())
                        .eq(AgentPromptTemplate::getStatus, 1));
        for (AgentPromptTemplate active : actives) {
            active.setStatus(2);
            promptMapper.updateById(active);
        }

        target.setStatus(1);
        promptMapper.updateById(target);
        log.info("Rolled back prompt for agent {} to version {} (id={})",
                target.getAgentCode(), target.getVersion(), templateId);
    }
}
