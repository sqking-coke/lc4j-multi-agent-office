package com.agentoffice.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.agentoffice.security.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：注册多租户 SQL 拦截器和 MySQL 分页插件。
 * 租户拦截器自动在 SQL 中追加 tenant_id 条件（排除系统表如 sys_role / sys_permission 等）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // Tenant interceptor
        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.getTenantId();
                return new LongValue(tenantId != null ? tenantId : 0);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // Tables without tenant isolation
                return "sys_role".equalsIgnoreCase(tableName)
                        || "sys_permission".equalsIgnoreCase(tableName)
                        || "sys_role_permission".equalsIgnoreCase(tableName)
                        || "task_decompose_template".equalsIgnoreCase(tableName)
                        || "llm_token_daily_stat".equalsIgnoreCase(tableName)
                        || "agent_info".equalsIgnoreCase(tableName)
                        || "agent_prompt_template".equalsIgnoreCase(tableName)
                        || "approval_record".equalsIgnoreCase(tableName);
            }
        });
        interceptor.addInnerInterceptor(tenantInterceptor);

        // Pagination
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
