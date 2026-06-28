package com.agentoffice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agent_prompt_template")
public class AgentPromptTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateCode;
    private String templateContent;
    private String version;
    private String agentCode;
    private Integer status;
    private BigDecimal successRate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
