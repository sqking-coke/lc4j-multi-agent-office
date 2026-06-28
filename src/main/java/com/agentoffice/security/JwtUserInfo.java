package com.agentoffice.security;

import lombok.Data;
import java.util.List;

/** JWT Token 解析后的用户身份信息载体。 */
@Data
public class JwtUserInfo {
    private Long userId;
    private Long tenantId;
    private String roleCode;
    private List<String> permissions;
}
