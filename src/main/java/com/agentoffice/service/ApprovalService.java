package com.agentoffice.service;

import com.agentoffice.entity.ApprovalRecord;
import com.agentoffice.mapper.ApprovalRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批服务：创建审批记录，支持通过/驳回操作，查询待审批列表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRecordMapper approvalRecordMapper;

    @Transactional
    public ApprovalRecord createRecord(Long taskItemId, Long approverId, String agentOutputSnapshot) {
        ApprovalRecord record = new ApprovalRecord();
        record.setTaskItemId(taskItemId);
        record.setApproverId(approverId);
        record.setStatus(0); // pending
        record.setAgentOutputSnapshot(agentOutputSnapshot);
        record.setCreateTime(LocalDateTime.now());
        approvalRecordMapper.insert(record);
        log.info("Approval record created for task item {}", taskItemId);
        return record;
    }

    @Transactional
    public void approve(Long recordId, Long approverId, String comment) {
        ApprovalRecord record = approvalRecordMapper.selectById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("审批记录不存在");
        }
        record.setStatus(1); // approved
        record.setApproverId(approverId);
        record.setComment(comment);
        record.setResolveTime(LocalDateTime.now());
        approvalRecordMapper.updateById(record);
        log.info("Approval record {} approved by {}", recordId, approverId);
    }

    @Transactional
    public void reject(Long recordId, Long approverId, String comment) {
        ApprovalRecord record = approvalRecordMapper.selectById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("审批记录不存在");
        }
        record.setStatus(2); // rejected
        record.setApproverId(approverId);
        record.setComment(comment);
        record.setResolveTime(LocalDateTime.now());
        approvalRecordMapper.updateById(record);
        log.info("Approval record {} rejected by {}", recordId, approverId);
    }

    public List<ApprovalRecord> getPendingApprovals(Long approverId) {
        return approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getApproverId, approverId)
                        .eq(ApprovalRecord::getStatus, 0)
                        .orderByAsc(ApprovalRecord::getCreateTime));
    }
}
