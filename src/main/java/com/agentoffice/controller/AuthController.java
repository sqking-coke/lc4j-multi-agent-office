package com.agentoffice.controller;

import com.agentoffice.entity.SysUser;
import com.agentoffice.mapper.SysRoleMapper;
import com.agentoffice.mapper.SysUserMapper;
import com.agentoffice.security.JwtTokenProvider;
import com.agentoffice.util.ApiResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "认证管理", description = "用户登录、Token刷新")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回 AccessToken 和 RefreshToken")
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@RequestBody LoginRequest request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (user == null || user.getStatus() == 0) {
            return ApiResult.error("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ApiResult.error("用户名或密码错误");
        }

        List<String> permissions = roleMapper.getPermissionCodesByRoleId(user.getRoleId());
        String token = jwtTokenProvider.generateToken(
                user.getId(), user.getTenantId(), resolveRoleCode(user.getRoleId()), permissions);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(token);
        resp.setRefreshToken(refreshToken);
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        return ApiResult.success(resp);
    }

    @Operation(summary = "刷新Token", description = "使用 RefreshToken 获取新的 AccessToken")
    @PostMapping("/refresh")
    public ApiResult<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            return ApiResult.error("Token已过期，请重新登录");
        }
        var userInfo = jwtTokenProvider.parseToken(request.getRefreshToken());
        SysUser user = userMapper.selectById(userInfo.getUserId());
        if (user == null || user.getStatus() == 0) {
            return ApiResult.error("用户不存在或已禁用");
        }

        List<String> permissions = roleMapper.getPermissionCodesByRoleId(user.getRoleId());
        String token = jwtTokenProvider.generateToken(
                user.getId(), user.getTenantId(), resolveRoleCode(user.getRoleId()), permissions);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(token);
        resp.setRefreshToken(refreshToken);
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        return ApiResult.success(resp);
    }

    private String resolveRoleCode(Long roleId) {
        return switch (roleId.intValue()) {
            case 1 -> "ADMIN";
            case 2 -> "MANAGER";
            default -> "USER";
        };
    }

    @Data
    @Schema(description = "登录请求")
    public static class LoginRequest {
        @Schema(description = "用户名", example = "admin")
        private String username;
        @Schema(description = "密码", example = "admin123")
        private String password;
    }

    @Data
    @Schema(description = "刷新Token请求")
    public static class RefreshRequest {
        @Schema(description = "刷新令牌", example = "eyJhbGciOiJIUzI1NiJ9...")
        private String refreshToken;
    }

    @Data
    @Schema(description = "登录响应")
    public static class LoginResponse {
        @Schema(description = "访问令牌")
        private String accessToken;
        @Schema(description = "刷新令牌")
        private String refreshToken;
        @Schema(description = "用户名")
        private String username;
        @Schema(description = "真实姓名")
        private String realName;
    }
}
