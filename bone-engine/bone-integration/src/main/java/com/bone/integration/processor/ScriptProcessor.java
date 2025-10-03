package com.bone.integration.processor;

import com.bone.integration.flow.node.ScriptNode;
import com.bone.integration.uitls.ScriptExecutor;
import lombok.Data;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

@Data
public class ScriptProcessor implements Processor {
    private ScriptNode scriptNode;

    public ScriptProcessor(ScriptNode scriptNode) {
        this.scriptNode = scriptNode;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String debugConnId = exchange.getIn().getHeader("debugConnId", String.class);
        String result = ScriptExecutor.execute(exchange, debugConnId,exchange.getIn().getBody(), exchange.getIn().getHeaders(),  scriptNode.getExpression(), scriptNode.getLanguage());
        exchange.getIn().setBody(result);
    }
}