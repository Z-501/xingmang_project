package com.example.xingmang.config;

import com.baidu.aip.bodyanalysis.AipBodyAnalysis;
import com.example.xingmang.config.BaiduBodyAnalysisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BaiduBodyAnalysisConfig {

    @Bean
    public AipBodyAnalysis aipBodyAnalysis(BaiduBodyAnalysisProperties properties) {
        AipBodyAnalysis client = new AipBodyAnalysis(
                properties.getAppId(),
                properties.getApiKey(),
                properties.getSecretKey()
        );

        client.setConnectionTimeoutInMillis(properties.getConnectionTimeoutMillis());
        client.setSocketTimeoutInMillis(properties.getSocketTimeoutMillis());

        return client;
    }
}