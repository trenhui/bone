package com.bone.integration.flow.visitor.camel.builder.component;

import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.visitor.camel.context.FlowContext;
import com.bone.integration.flow.node.DirectNode;
import org.springframework.stereotype.Component;

@Component
public class DirectBuilder extends CamelComponentBuilder<DirectNode> {
    @Override
    public void process(DirectNode node, CamelBuilderContext context) {
        String uri = "direct:" + getFullUri(node.getUri(), context.getFlowContext());
        this.build(context.getFlowContext().getRouteId(), uri, context);
    }

    private String getFullUri(String uri, FlowContext flowContext) {
        return getUri(uri + "/" + flowContext.getAppCode(), flowContext);
    }

    private String getUri(String uri, FlowContext flowContext) {
        return flowContext.isSupportMultiVersion() ? uri + "/" + flowContext.getVersion() : uri;
    }
}