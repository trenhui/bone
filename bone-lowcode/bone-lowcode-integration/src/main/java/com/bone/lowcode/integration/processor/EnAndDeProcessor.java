package com.bone.lowcode.integration.processor;

import com.bone.lowcode.integration.flow.node.EnAndDeNode;
import com.bone.lowcode.integration.uitls.EnAndDeExecutor;
import lombok.Data;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;


@Data
public class EnAndDeProcessor implements Processor {
    private EnAndDeNode enAndDeNode;

    public EnAndDeProcessor(EnAndDeNode enAndDeNode) {
        this.enAndDeNode = enAndDeNode;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String content = exchange.getIn().getBody(String.class);
        String result = EnAndDeExecutor.execute(content, enAndDeNode);
        exchange.getIn().setBody(result);
    }
}