package com.agentoffice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_task")
public class AgentTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String taskName;
    private String taskContent;
    private String coopMode;
    private Integer priority;
    private Integer timeoutSeconds;
    private Integer taskStatus;
    private String resultSummary;
    private Long submitUserId;
    private Long tenantId;
    private String traceId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}
