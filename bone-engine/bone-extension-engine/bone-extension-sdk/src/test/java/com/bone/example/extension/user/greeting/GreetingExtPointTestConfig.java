package com.bone.example.extension.user.greeting;

import com.bone.engine.extension.proxy.ExtPointProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * GreetingExtPoint专用测试配置类
 * 提供所有必要的Bean定义
 */
@Configuration
public class GreetingExtPointTestConfig {
    
    /**
     * 提供扩展点代理工厂
     */
    @Bean
    public ExtPointProxyFactory extPointProxyFactory() {
        return new ExtPointProxyFactory();
    }
    
    /**
     * 确保扩展点接口可被注入
     */
    @Bean
    public GreetingExtPoint greetingExtPoint() {
        // 直接创建一个简单的实现用于测试
        return context -> {
            String userName = context.getAttribute("userName") != null ? 
                context.getAttribute("userName").toString() : "Guest";
            return "Hello, " + userName + "!";
        };
    }
}