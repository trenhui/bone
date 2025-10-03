package com.bone.integration.flow.visitor.camel.builder.component;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.node.NettyHttpNode;
import org.apache.camel.spi.RestConfiguration;
import org.springframework.stereotype.Component;

@Component
public class NettyHttpBuilder extends CamelComponentBuilder<NettyHttpNode> {

    @Override
    public void process(NettyHttpNode node, CamelBuilderContext context) {
        String uri = getUri(node, context);
        this.build(context.getFlowContext().getRouteId(), uri, context);
    }

    private String getUri(NettyHttpNode node, CamelBuilderContext context) {
        RestConfiguration restConfiguration = context.getCamelContext().getRestConfiguration();
        String protocol = node.getProtocol();
        String uri;
        if (protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https")) {
            uri = restConfiguration.getComponent() + ":" + restConfiguration.getScheme() +
                    "://" + restConfiguration.getHost() + ":" + restConfiguration.getPort() + getFullUri(node.getUri(), context) + "?";
        } else {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }

        return uri;
    }

    private String getFullUri(String uri, CamelBuilderContext context) {
        String nodeUri = getNodeUri(uri, context);
        if (nodeUri.startsWith("/")) {
            return "/" + context.getFlowContext().getAppCode() + nodeUri;
        } else {
            return "/" + context.getFlowContext().getAppCode() + "/" + nodeUri;
        }
    }

    private String getNodeUri(String uri, CamelBuilderContext context) {
        return context.getFlowContext().isSupportMultiVersion() ? "/" + context.getFlowContext().getVersion() + uri : uri;
    }
}