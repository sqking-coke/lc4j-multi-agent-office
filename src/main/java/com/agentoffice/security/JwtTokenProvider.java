package com.agentoffice.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

/**
 * JWT Token 提供者：负责 AccessToken / RefreshToken 的生成、解析与校验。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expiration;
    private final long refreshExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
    }

    /** 生成 AccessToken，Payload 包含 userId、tenantId、roleCode 和权限列表。 */
    public String generateToken(Long userId, Long tenantId, String roleCode, List<String> permissions) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .claim("roleCode", roleCode)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    /** 生成 RefreshToken，仅包含 userId，有效期比 AccessToken 更长。 */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpiration))
                .signWith(key)
                .compact();
    }

    /** 解析 Token 为 JwtUserInfo，提取 userId、tenantId、roleCode 和权限列表。 */
    public JwtUserInfo parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        JwtUserInfo info = new JwtUserInfo();
        info.setUserId(Long.valueOf(claims.getSubject()));
        info.setTenantId(claims.get("tenantId", Long.class));
        info.setRoleCode(claims.get("roleCode", String.class));
        info.setPermissions(claims.get("permissions", List.class));
        return info;
    }

    /** 校验 Token 签名与有效期，无效/过期返回 false。 */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }
}
