package com.agentoffice.service;

import com.agentoffice.agent.BizAgent;
import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.entity.AgentInfo;
import com.agentoffice.mapper.AgentInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Agent 管理服务：负责 Agent 的注册、查询、更新、注销、启停和运行指标聚合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentInfoMapper agentInfoMapper;
    private final AgentRegistry agentRegistry;

    public List<AgentInfo> listEnabled() {
        return agentInfoMapper.selectList(
                new LambdaQueryWrapper<AgentInfo>().eq(AgentInfo::getStatus, 1));
    }

    public AgentInfo getByAgentCode(String agentCode) {
        return agentInfoMapper.selectOne(
                new LambdaQueryWrapper<AgentInfo>().eq(AgentInfo::getAgentCode, agentCode));
    }

    @Transactional
    public AgentInfo register(AgentInfo info) {
        AgentInfo existing = agentInfoMapper.selectOne(
                new LambdaQueryWrapper<AgentInfo>().eq(AgentInfo::getAgentCode, info.getAgentCode()));
        if (existing != null) {
            throw new IllegalArgumentException("Agent编码已存在: " + info.getAgentCode());
        }
        info.setStatus(1);
        agentInfoMapper.insert(info);
        log.info("Agent registered: {}", info.getAgentCode());
        return info;
    }

    @Transactional
    public void update(String agentCode, AgentInfo update) {
        AgentInfo info = agentInfoMapper.selectOne(
                new LambdaQueryWrapper<AgentInfo>().eq(AgentInfo::getAgentCode, agentCode));
        if (info == null) {
            throw new IllegalArgumentException("Agent不存在: " + agentCode);
        }
        if (update.getAgentName() != null) info.setAgentName(update.getAgentName());
        if (update.getAgentCapability() != null) info.setAgentCapability(update.getAgentCapability());
        if (update.getConfigJson() != null) info.setConfigJson(update.getConfigJson());
        if (update.getPriority() != null) info.setPriority(update.getPriority());
        if (update.getMaxConcurrency() != null) info.setMaxConcurrency(update.getMaxConcurrency());
        agentInfoMapper.updateById(info);
    }

    @Transactional
    public void unregister(String agentCode) {
        agentRegistry.unregister(agentCode);
        AgentInfo info = agentInfoMapper.selectOne(
                new LambdaQueryWrapper<AgentInfo>().eq(AgentInfo::getAgentCode, agentCode));
        if (info != null) {
            info.setStatus(0);
            agentInfoMapper.updateById(info);
        }
    }

    @Transactional
    public void toggleStatus(String agentCode, Integer status) {
        AgentInfo info = agentInfoMapper.selectOne(
                new LambdaQueryWrapper<AgentInfo>().eq(AgentInfo::getAgentCode, agentCode));
        if (info == null) {
            throw new IllegalArgumentException("Agent不存在: " + agentCode);
        }
        info.setStatus(status);
        agentInfoMapper.updateById(info);
    }

    public List<Map<String, Object>> getMetrics() {
        return agentRegistry.getAllConfigs().stream().map(config -> {
            Map<String, Object> m = new HashMap<>();
            m.put("agentCode", config.getAgentCode());
            m.put("agentName", config.getAgentName());
            m.put("priority", config.getPriority());
            m.put("maxConcurrency", config.getMaxConcurrency());
            m.put("capabilities", config.getCapability().getCapabilities());
            m.put("healthy", agentRegistry.getAgent(config.getAgentCode())
                    .map(BizAgent::healthCheck).orElse(false));
            return m;
        }).toList();
    }
}
