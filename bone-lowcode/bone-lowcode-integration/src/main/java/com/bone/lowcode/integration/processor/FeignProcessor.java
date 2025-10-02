package com.bone.lowcode.integration.processor;

import com.bone.lowcode.integration.flow.node.FeignNode;
import com.bone.lowcode.integration.uitls.FeignExecutor;
import lombok.Data;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.web.client.RestTemplate;

@Data
public class FeignProcessor implements Processor {

    private FeignNode feignNode;
    private RestTemplate restTemplate;

    // 通过构造函数注入 Feign 客户端
    public FeignProcessor(FeignNode feignNode, RestTemplate restTemplate) {
        this.feignNode = feignNode;
        this.restTemplate = restTemplate;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String content = FeignExecutor.execute(feignNode, exchange, restTemplate);
        exchange.getMessage().setBody(content);
    }

}


