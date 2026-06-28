package com.agentoffice.service;

import com.agentoffice.entity.AgentTask;
import com.agentoffice.entity.AgentTaskItem;
import com.agentoffice.lc4j.graph.OfficeAgentState;
import com.agentoffice.lc4j.graph.OfficeStateGraph;
import com.agentoffice.mapper.AgentTaskItemMapper;
import com.agentoffice.mapper.AgentTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 任务服务 —— 适配版：DispatchHub → OfficeStateGraph。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final OfficeStateGraph stateGraph;
    private final AgentTaskMapper taskMapper;
    private final AgentTaskItemMapper itemMapper;

    public AgentTask submit(String taskName, String taskContent, Long userId, Long tenantId) {
        // 创建主任务记录
        AgentTask task = new AgentTask();
        task.setTaskNo("AT" + System.currentTimeMillis());
        task.setTaskName(taskName);
        task.setTaskContent(taskContent);
        task.setTaskStatus(0);
        task.setSubmitUserId(userId);
        task.setTenantId(tenantId);
        taskMapper.insert(task);

        try {
            var future = stateGraph.submitTask(taskName, taskContent, userId, tenantId);
            Optional<OfficeAgentState> stateOpt = future.get(300, TimeUnit.SECONDS);

            if (stateOpt.isPresent()) {
                var state = stateOpt.get();
                task.setTraceId(state.getTraceId());
                task.setCoopMode(state.getCoopMode());
                int finalStatus = (int) state.data().getOrDefault(OfficeAgentState.KEY_FINAL_STATUS, 1);
                task.setTaskStatus(finalStatus);
                task.setResultSummary((String) state.data().getOrDefault(OfficeAgentState.KEY_FINAL_SUMMARY, ""));
                task.setFinishTime(LocalDateTime.now());
            } else {
                task.setTaskStatus(2);
                task.setResultSummary("Execution returned no result");
                task.setFinishTime(LocalDateTime.now());
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Task execution failed: {}", task.getTaskNo(), e);
            task.setTaskStatus(2);
            task.setResultSummary("Execution error: " + e.getMessage());
            task.setFinishTime(LocalDateTime.now());
        }

        taskMapper.updateById(task);
        return task;
    }

    public Page<AgentTask> list(int page, int size, Integer status, String coopMode) {
        Page<AgentTask> p = new Page<>(page, size);
        LambdaQueryWrapper<AgentTask> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(AgentTask::getTaskStatus, status);
        if (coopMode != null) wrapper.eq(AgentTask::getCoopMode, coopMode);
        wrapper.orderByDesc(AgentTask::getCreateTime);
        taskMapper.selectPage(p, wrapper);
        return p;
    }

    public AgentTask getTask(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    public List<AgentTaskItem> getItems(Long taskId) {
        return itemMapper.selectList(new LambdaQueryWrapper<AgentTaskItem>()
                .eq(AgentTaskItem::getTaskId, taskId));
    }

    @Transactional
    public void cancel(Long taskId) {
        AgentTask task = taskMapper.selectById(taskId);
        if (task != null && task.getTaskStatus() == 0) {
            task.setTaskStatus(3);
            task.setFinishTime(LocalDateTime.now());
            taskMapper.updateById(task);
            log.info("Task {} cancelled", taskId);
        }
    }

    @Transactional
    public void approveItem(Long taskItemId) {
        AgentTaskItem item = itemMapper.selectById(taskItemId);
        if (item == null) throw new IllegalArgumentException("子任务不存在");
        item.setStatus(2);
        item.setFinishTime(LocalDateTime.now());
        itemMapper.updateById(item);
        log.info("Task item {} approved", taskItemId);
    }

    @Transactional
    public void rejectItem(Long taskItemId, String reason) {
        AgentTaskItem item = itemMapper.selectById(taskItemId);
        if (item == null) throw new IllegalArgumentException("子任务不存在");
        item.setStatus(3);
        item.setErrorMsg(reason != null ? reason : "人工驳回");
        item.setFinishTime(LocalDateTime.now());
        itemMapper.updateById(item);
        log.info("Task item {} rejected: {}", taskItemId, reason);
    }
}
