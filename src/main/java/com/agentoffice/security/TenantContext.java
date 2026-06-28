package com.agentoffice.security;

/**
 * 基于 ThreadLocal 的租户隔离上下文，存储当前请求的 tenantId 和 userId。
 * 由 JwtAuthenticationFilter 设置，请求结束后必须调用 clear() 清理。
 */
public class TenantContext {
    private static final ThreadLocal<Long> TENANT_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_HOLDER = new ThreadLocal<>();

    /** 设置当前线程的租户 ID。 */
    public static void setTenantId(Long tenantId) {
        TENANT_HOLDER.set(tenantId);
    }

    /** 获取当前线程的租户 ID。 */
    public static Long getTenantId() {
        return TENANT_HOLDER.get();
    }

    /** 设置当前线程的用户 ID。 */
    public static void setUserId(Long userId) {
        USER_HOLDER.set(userId);
    }

    /** 获取当前线程的用户 ID。 */
    public static Long getUserId() {
        return USER_HOLDER.get();
    }

    /** 清理当前线程的租户和用户信息，防止内存泄漏。 */
    public static void clear() {
        TENANT_HOLDER.remove();
        USER_HOLDER.remove();
    }
}
