package com.agentoffice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_task_item")
public class AgentTaskItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String agentCode;
    private String itemContent;
    private String itemResult;
    private Integer status;
    private String errorMsg;
    private Integer retryCount;
    private Integer maxRetry;
    private Integer costTime;
    private Long tenantId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}
