package com.agentoffice.service;

import com.agentoffice.entity.AgentStatReport;
import com.agentoffice.entity.LlmTokenDailyStat;
import com.agentoffice.mapper.AgentStatReportMapper;
import com.agentoffice.mapper.LlmTokenDailyStatMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 报表服务：统计报表和 Token 消耗记录的查询与写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AgentStatReportMapper reportMapper;
    private final LlmTokenDailyStatMapper tokenStatMapper;

    public List<AgentStatReport> listReports(String type, String agentCode) {
        LambdaQueryWrapper<AgentStatReport> wrapper = new LambdaQueryWrapper<>();
        if (type != null) wrapper.eq(AgentStatReport::getReportType, type);
        if (agentCode != null) wrapper.eq(AgentStatReport::getAgentCode, agentCode);
        wrapper.orderByDesc(AgentStatReport::getCreateTime);
        return reportMapper.selectList(wrapper);
    }

    public List<LlmTokenDailyStat> getTokenUsage(String agentCode) {
        LambdaQueryWrapper<LlmTokenDailyStat> wrapper = new LambdaQueryWrapper<>();
        if (agentCode != null) wrapper.eq(LlmTokenDailyStat::getAgentCode, agentCode);
        wrapper.orderByDesc(LlmTokenDailyStat::getStatDate);
        return tokenStatMapper.selectList(wrapper);
    }

    public List<AgentStatReport> getAgentStats(String agentCode, String type) {
        LambdaQueryWrapper<AgentStatReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentStatReport::getReportType, type);
        if (agentCode != null) wrapper.eq(AgentStatReport::getAgentCode, agentCode);
        wrapper.orderByDesc(AgentStatReport::getCreateTime);
        return reportMapper.selectList(wrapper);
    }

    public void insertReport(AgentStatReport report) {
        reportMapper.insert(report);
    }
}
