package com.agentoffice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_info")
public class AgentInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentCode;
    private String agentName;
    private String agentCapability;
    private String configJson;
    private Integer priority;
    private Integer maxConcurrency;
    private Integer avgCostTime;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
