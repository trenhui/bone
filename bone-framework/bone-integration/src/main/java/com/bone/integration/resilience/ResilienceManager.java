package com.bone.integration.resilience;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.springframework.stereotype.Component;

@Component
public class ResilienceManager extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // 捕获全局异常，记录错误日志
        onException(Exception.class)
                .log("An unexpected error occurred: ${exception.message}")
                .handled(true)
                .to("log:error");

        // 创建 Resilience4j 配置
        Resilience4jConfigurationDefinition resilienceConfig = new Resilience4jConfigurationDefinition();
        resilienceConfig.setFailureRateThreshold("50");  // 设置失败率阈值为50%
        resilienceConfig.setTimeoutEnabled("true");      // 启用超时功能
        resilienceConfig.setTimeoutDuration("60000");    // 设置超时为60秒
        resilienceConfig.setPermittedNumberOfCallsInHalfOpenState("5"); // 半开状态允许的请求数
        resilienceConfig.setSlidingWindowSize("10");  // 窗口大小
        resilienceConfig.setMinimumNumberOfCalls("5");  // 最小调用次数，达到才开始计算失败率
        resilienceConfig.setWaitDurationInOpenState("10000"); // 断路器打开后的等待时间

        // 使用 Resilience4j Circuit Breaker 配置
        from("direct:start")
                .circuitBreaker()
                .resilience4jConfiguration(resilienceConfig)  // 使用自定义的 Resilience4j 配置
                .to("direct:processMessage")
                .onFallback()  // 定义断路器触发时的回退处理
                .log("Circuit Breaker is open, fallback initiated for message: ${body}")
                .process(exchange -> {
                    // 这里可以返回默认值，执行降级处理
                    exchange.getMessage().setBody("Fallback response: Service is currently unavailable.");
                })
                .to("direct:fallback")
                .end()
                .log("Processing message with Resilience4j Circuit Breaker")
                .to("mock:result");

        // 业务处理路由
        from("direct:processMessage")
                .log("Processing the main message route for message: ${body}")
                .doTry()
                .to("log:processMessage") // 可以添加实际的业务处理逻辑，例如调用服务等
                .doCatch(Exception.class)
                .log("Error occurred during processing: ${exception.message}")
                .process(exchange -> {
                    // 这里可以处理异常的情况，返回特定的错误响应等
                    exchange.getMessage().setBody("Error: Unable to process message.");
                })
                .end();

        // 回退处理路由
        from("direct:fallback")
                .log("Executing fallback logic for message: ${body}")
                // 可以添加实际的回退逻辑，例如返回默认值或者发出告警等
                .to("log:fallbackMessage");
    }
}