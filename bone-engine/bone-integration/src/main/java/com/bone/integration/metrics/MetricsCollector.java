package com.bone.integration.metrics;

import org.apache.camel.Exchange;

public class MetricsCollector {

    public void collect(Exchange exchange) {
        String routeId = exchange.getFromRouteId();
        // 收集处理时间、成功率、失败次数等指标
        System.out.println("Collecting metrics for route: " + routeId);
    }
}
