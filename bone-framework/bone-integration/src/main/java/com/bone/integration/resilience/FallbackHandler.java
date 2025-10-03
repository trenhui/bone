package com.bone.integration.resilience;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class FallbackHandler implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        // 降级处理逻辑
        exchange.getIn().setBody("Fallback response");
    }
}
