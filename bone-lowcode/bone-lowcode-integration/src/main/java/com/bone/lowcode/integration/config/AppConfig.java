package com.bone.lowcode.integration.config;

import com.bone.lowcode.integration.uitls.WebApplicationContextUtils;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;

@Configuration
public class AppConfig {

    // 定义 RestTemplate Bean，提供给 Spring 管理
    @Bean
    @LoadBalanced  // 如果你使用负载均衡（如 Eureka），加上 @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public static WebApplicationContext webApplicationContext(ApplicationContext applicationContext) {
        WebApplicationContext context = (WebApplicationContext) applicationContext;
        WebApplicationContextUtils.setWebApplicationContext(context);  // 将 WebApplicationContext 注入到静态类
        return context;
    }
}
