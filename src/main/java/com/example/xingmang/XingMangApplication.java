package com.example.xingmang;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.xingmang.mapper")
public class XingMangApplication {

    public static void main(String[] args) {
        // 启动SpringBoot，内置Tomcat服务器（默认端口8080）
        SpringApplication.run(XingMangApplication.class, args);
    }

}
