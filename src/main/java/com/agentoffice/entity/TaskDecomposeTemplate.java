package com.agentoffice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("task_decompose_template")
public class TaskDecomposeTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateName;
    private String triggerKeywords;
    private String subTasksJson;
    private String coopMode;
    private Integer priority;
    private Integer status;
}
