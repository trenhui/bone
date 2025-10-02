package com.bone.lowcode.integration.processor;

import com.bone.lowcode.integration.flow.node.WebServiceNode;
import com.bone.lowcode.integration.uitls.WebServiceExecutor;
import lombok.Data;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;


@Data
public class WebServiceProcessor implements Processor {
    private WebServiceNode webServiceNode;

    public WebServiceProcessor(WebServiceNode webServiceNode) {
        this.webServiceNode = webServiceNode;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String content = exchange.getIn().getBody(String.class);
        String result = WebServiceExecutor.execute(content, webServiceNode);
        exchange.getIn().setBody(result);
    }
}