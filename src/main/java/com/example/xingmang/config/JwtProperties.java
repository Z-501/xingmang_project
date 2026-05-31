package com.example.xingmang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** JWT signing secret. Configure through APP_JWT_SECRET in production. */
    private String secret;

    /** Access token validity period in milliseconds. */
    private long accessTokenExpirationMs = 30 * 60 * 1000L;

    /** Refresh token validity period in milliseconds. */
    private long refreshTokenExpirationMs = 7 * 24 * 60 * 60 * 1000L;
}
