package com.bone.lowcode.integration.flow.visitor.camel.builder.component;

import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.lowcode.integration.flow.node.HttpNode;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;

import static org.apache.camel.builder.Builder.constant;

@Component
public class HttpBuilder extends CamelComponentBuilder<HttpNode> {

    @Override
    public void process(HttpNode node, CamelBuilderContext context) {

        String requestUri = "extend-" + node.getUri();
        String letter = "?";
        if (requestUri.contains("?")) {
            letter = "&";
        }

        requestUri = requestUri
                + letter
                + "bridgeEndpoint=" + node.isBridgeEndpoint()
                + "&httpMethod=" + node.getMethod()
                + "&httpClient.connectTimeout=5000"
                + "&httpClient.responseTimeout=10000";


        build("", requestUri, context, (processorDefinition)-> {
            processorDefinition.setHeader("Content-Type", constant("application/" +  node.getContextType() + "; charset=UTF-8"));
            context.writeOutput(".setHeader('Content-Type', constant('application/")
                    .writeOutput(node.getContextType()).writeOutput("; charset=UTF-8'))\n");
        });
    }
}