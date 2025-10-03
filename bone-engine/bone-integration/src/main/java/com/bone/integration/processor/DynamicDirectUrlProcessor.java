package com.bone.integration.processor;

import com.bone.integration.application.service.IDirectNodeManagerService;
import com.bone.integration.application.service.impl.CamelRouteServiceImpl;
import com.bone.integration.common.Constants;
import com.bone.integration.enums.EnvEnum;
import com.bone.integration.flow.node.RecipientListNode;
import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ServiceStatus;

public class DynamicDirectUrlProcessor implements Processor {

    private final CamelBuilderContext context;
    private final IDirectNodeManagerService iDirectNodeManagerService;
    private final RecipientListNode node;

    public DynamicDirectUrlProcessor(CamelBuilderContext context, IDirectNodeManagerService iDirectNodeManagerService, RecipientListNode recipientListNode) {
        this.context = context;
        this.iDirectNodeManagerService = iDirectNodeManagerService;
        this.node = recipientListNode;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        exchange.getIn().setHeader(Constants.DYNAMIC_DIRECT_URI, getRouteUri());
    }

    private String getRouteUri() {
        // 目前RecipientList只支持direct路由
        EnvEnum env = context.getFlowContext().getEnv();
        CamelRouteServiceImpl.FlowProcess process = iDirectNodeManagerService.findRouteByDirectUri(env, node.getExpression());
        if (process == null) {
            throw new RuntimeException("No route found for direct uri: " + node.getExpression());
        }

        if (!process.isSupportMultiVersion()) {
            return node.getExpression() + "/" + context.getFlowContext().getAppCode();
        }

        ServiceStatus status = context.getCamelContext().getRouteController().getRouteStatus(process.getRouteId());
        if (status != ServiceStatus.Started) {
            throw new RuntimeException("DynamicRouterProcessor route not started: " + process.getRouteId());
        }

        return node.getExpression() + "/" + context.getFlowContext().getAppCode() + "/" + process.getVersion();
    }
}
