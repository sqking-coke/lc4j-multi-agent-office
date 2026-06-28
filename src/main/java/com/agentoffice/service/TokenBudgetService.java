package com.agentoffice.service;

import com.agentoffice.agent.registry.AgentRegistry;
import com.agentoffice.entity.LlmTokenDailyStat;
import com.agentoffice.mapper.LlmTokenDailyStatMapper;
import com.agentoffice.monitor.AlertService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Token 预算服务：检查 Agent 当日 Token 消耗是否超出预算，触发分级告警（80% 预警 / 100% 熔断）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBudgetService {

    private final LlmTokenDailyStatMapper tokenStatMapper;
    private final AgentRegistry agentRegistry;
    private final AlertService alertService;

    public enum BudgetStatus { OK, WARN_80, BREAK_100 }

    /**
     * Check the token budget status for a given agent today.
     */
    public BudgetStatus checkBudget(String agentCode) {
        return agentRegistry.getConfig(agentCode)
                .map(config -> {
                    long budget = config.getDailyTokenBudget();
                    if (budget <= 0) return BudgetStatus.OK;

                    long used = getTodayTokenUsage(agentCode);
                    double percent = (double) used / budget * 100;

                    if (percent >= 100) {
                        log.warn("Token budget BREAK for agent {}: {}/{} ({}%)",
                                agentCode, used, budget, String.format("%.1f", percent));
                        alertService.alertTokenBudget(agentCode, (int) percent);
                        return BudgetStatus.BREAK_100;
                    }
                    if (percent >= 80) {
                        log.warn("Token budget WARN for agent {}: {}/{} ({}%)",
                                agentCode, used, budget, String.format("%.1f", percent));
                        alertService.alertTokenBudget(agentCode, (int) percent);
                        return BudgetStatus.WARN_80;
                    }
                    return BudgetStatus.OK;
                })
                .orElse(BudgetStatus.OK);
    }

    private long getTodayTokenUsage(String agentCode) {
        LlmTokenDailyStat stat = tokenStatMapper.selectOne(
                new LambdaQueryWrapper<LlmTokenDailyStat>()
                        .eq(LlmTokenDailyStat::getStatDate, LocalDate.now())
                        .eq(LlmTokenDailyStat::getAgentCode, agentCode));
        return stat != null ? stat.getTotalTokens() : 0;
    }
}
