package com.bone.integration.processor;

import com.bone.integration.flow.node.WebServiceNode;
import com.bone.integration.uitls.WebServiceExecutor;
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