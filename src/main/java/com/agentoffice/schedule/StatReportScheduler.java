package com.agentoffice.schedule;

import com.agentoffice.entity.AgentStatReport;
import com.agentoffice.entity.AgentTask;
import com.agentoffice.mapper.AgentTaskMapper;
import com.agentoffice.service.ReportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class StatReportScheduler {

    private final AgentTaskMapper taskMapper;
    private final ReportService reportService;

    /**
     * Generate daily agent statistics at 1:00 AM.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void generateDailyReport() {
        log.info("Generating daily agent stat report...");
        generateReport("DAILY", LocalDate.now().minusDays(1), LocalDate.now());
    }

    /**
     * Generate weekly report every Monday at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * MON")
    public void generateWeeklyReport() {
        log.info("Generating weekly agent stat report...");
        generateReport("WEEKLY", LocalDate.now().minusWeeks(1), LocalDate.now());
    }

    /**
     * Generate monthly report on the 1st at 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 1 * ?")
    public void generateMonthlyReport() {
        log.info("Generating monthly agent stat report...");
        generateReport("MONTHLY", LocalDate.now().minusMonths(1), LocalDate.now());
    }

    private void generateReport(String reportType, LocalDate from, LocalDate to) {
        List<AgentTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AgentTask>()
                .ge(AgentTask::getCreateTime, from.atStartOfDay())
                .lt(AgentTask::getCreateTime, to.atStartOfDay()));

        if (tasks.isEmpty()) {
            log.info("No tasks in period for {}", reportType);
            return;
        }

        // Group by agent_code via sub-tasks — simplified: use coop_mode to infer
        Map<String, List<AgentTask>> grouped = tasks.stream()
                .collect(Collectors.groupingBy(t -> t.getCoopMode() != null ? t.getCoopMode() : "UNKNOWN"));

        for (var entry : grouped.entrySet()) {
            List<AgentTask> group = entry.getValue();
            AgentStatReport report = new AgentStatReport();
            report.setReportType(reportType);
            report.setAgentCode(entry.getKey());
            report.setTotalTask(group.size());
            report.setSuccessTask((int) group.stream().filter(t -> t.getTaskStatus() == 1).count());
            report.setFailTask((int) group.stream().filter(t -> t.getTaskStatus() == 2).count());
            report.setAvgCostTime(0); // calculated elsewhere
            report.setCreateTime(LocalDateTime.now());
            reportService.insertReport(report);
        }
        log.info("Generated {} report: {} groups", reportType, grouped.size());
    }
}
