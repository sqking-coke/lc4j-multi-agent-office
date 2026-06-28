package com.agentoffice.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("llm_token_daily_stat")
public class LlmTokenDailyStat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate statDate;
    private String agentCode;
    private String model;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private BigDecimal estimatedCost;
    private Integer callCount;
    private Integer cacheHitCount;
}
