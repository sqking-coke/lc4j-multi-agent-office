package com.agentoffice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_stat_report")
public class AgentStatReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reportType;
    private String agentCode;
    private Integer totalTask;
    private Integer successTask;
    private Integer failTask;
    private Integer avgCostTime;
    private String promptVersion;
    private String reportContent;
    private Long tenantId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
