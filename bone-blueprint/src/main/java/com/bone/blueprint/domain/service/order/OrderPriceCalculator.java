package com.bone.blueprint.domain.service.order;

import com.bone.engine.extension.api.annotation.ExtensionPoint;
import java.math.BigDecimal;

@ExtensionPoint(
    name = "订单价格计算扩展点",
    description = "不同租户和场景下的订单价格计算",
    version = "1.0.0",
    transactional = false,
    timeout = 10,
    singleton = true
)
public interface OrderPriceCalculator {
    BigDecimal calculate(OrderPriceRequest request);
    
    class OrderPriceRequest {
        private BigDecimal baseAmount;
        private String tenant;
        private String scenario;
        
        public OrderPriceRequest(BigDecimal baseAmount, String tenant, String scenario) {
            this.baseAmount = baseAmount;
            this.tenant = tenant;
            this.scenario = scenario;
        }
        
        public BigDecimal getBaseAmount() {
            return baseAmount;
        }
        
        public String getTenant() {
            return tenant;
        }
        
        public String getScenario() {
            return scenario;
        }
    }
}