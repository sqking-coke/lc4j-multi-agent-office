-- Multi-Agent Office 数据库初始化
CREATE DATABASE IF NOT EXISTS agent_office DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE agent_office;

-- 主任务表
CREATE TABLE IF NOT EXISTS agent_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL COMMENT '任务编号',
    task_name VARCHAR(255) NOT NULL COMMENT '任务名称',
    task_content TEXT COMMENT '任务内容',
    task_status INT DEFAULT 0 COMMENT '0=待处理 1=完成 2=失败 3=取消',
    coop_mode VARCHAR(32) COMMENT '协同模式 PARALLEL/SERIAL/RELAY/CONDITION/LOOP',
    result_summary TEXT COMMENT '结果摘要',
    submit_user_id BIGINT COMMENT '提交用户ID',
    tenant_id BIGINT COMMENT '租户ID',
    trace_id VARCHAR(32) COMMENT '全链路追踪ID',
    finish_time DATETIME COMMENT '完成时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent协同任务主表';

-- 子任务表
CREATE TABLE IF NOT EXISTS agent_task_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '主任务ID',
    agent_code VARCHAR(64) COMMENT '处理Agent编码',
    item_content TEXT COMMENT '子任务内容',
    item_result TEXT COMMENT '执行结果JSON',
    status INT DEFAULT 0 COMMENT '0=PENDING 1=RUNNING 2=SUCCESS 3=FAILED 4=SKIPPED',
    error_msg VARCHAR(2000) COMMENT '错误信息',
    retry_count INT DEFAULT 0,
    max_retry INT DEFAULT 3,
    cost_time INT COMMENT '耗时ms',
    tenant_id BIGINT,
    finish_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent子任务表';

-- Agent 信息表
CREATE TABLE IF NOT EXISTS agent_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_code VARCHAR(64) NOT NULL UNIQUE,
    agent_name VARCHAR(128),
    capability JSON COMMENT '能力标签',
    model VARCHAR(64),
    temperature DOUBLE DEFAULT 0.3,
    max_tokens INT DEFAULT 2048,
    daily_token_budget BIGINT DEFAULT 100000,
    retry_max INT DEFAULT 3,
    timeout_seconds INT DEFAULT 300,
    priority INT DEFAULT 10,
    max_concurrency INT DEFAULT 5,
    status INT DEFAULT 1 COMMENT '0=禁用 1=启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent注册信息表';

-- Prompt 模板表
CREATE TABLE IF NOT EXISTS agent_prompt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128),
    system_prompt TEXT,
    user_prompt_template TEXT,
    version INT DEFAULT 1,
    status INT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Prompt模板表';

-- 任务拆解模板表
CREATE TABLE IF NOT EXISTS task_decompose_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(128),
    keywords VARCHAR(500) COMMENT '触发关键词,逗号分隔',
    coop_mode VARCHAR(32),
    sub_tasks JSON COMMENT '子任务定义',
    tenant_id BIGINT,
    status INT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务拆解模板表';

-- Token 日统计表
CREATE TABLE IF NOT EXISTS llm_token_daily_stat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL,
    agent_code VARCHAR(64) NOT NULL,
    model VARCHAR(64),
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    total_tokens BIGINT DEFAULT 0,
    call_count INT DEFAULT 0,
    cache_hit_count INT DEFAULT 0,
    estimated_cost DECIMAL(10,4) DEFAULT 0,
    UNIQUE KEY uk_date_agent_model (stat_date, agent_code, model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM Token日统计表';

-- 审批记录表
CREATE TABLE IF NOT EXISTS approval_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT,
    task_item_id BIGINT,
    approval_type VARCHAR(32) COMMENT 'APPROVE/REJECT',
    approver_id BIGINT,
    comment VARCHAR(1000),
    status INT DEFAULT 0 COMMENT '0=PENDING 1=APPROVED 2=REJECTED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';

-- 知识库文档表
CREATE TABLE IF NOT EXISTS kb_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_name VARCHAR(255),
    doc_content LONGTEXT,
    doc_type VARCHAR(32),
    status INT DEFAULT 1,
    tenant_id BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';
