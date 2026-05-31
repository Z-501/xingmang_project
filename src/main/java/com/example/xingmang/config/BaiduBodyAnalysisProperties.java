package com.example.xingmang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "baidu.body-analysis")
public class BaiduBodyAnalysisProperties {

    /**
     * 百度人体分析 APP_ID
     */
    private String appId;

    /**
     * 百度人体分析 API_KEY
     */
    private String apiKey;

    /**
     * 百度人体分析 SECRET_KEY
     */
    private String secretKey;

    /**
     * 连接超时，毫秒
     */
    private Integer connectionTimeoutMillis = 2000;

    /**
     * Socket 超时，毫秒
     */
    private Integer socketTimeoutMillis = 60000;
}
