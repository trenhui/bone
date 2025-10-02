package com.bone.lowcode.integration.processor;

import com.bone.lowcode.integration.application.service.IDirectNodeManagerService;
import com.bone.lowcode.integration.flow.node.DirectNode;
import org.apache.camel.*;

@SuppressWarnings("unchecked")
public class DynamicRouteLiteFlowProcessor implements Processor {

    private final IDirectNodeManagerService iDirectNodeManagerService;
    private final DirectNode directNode;

    public DynamicRouteLiteFlowProcessor(IDirectNodeManagerService iDirectNodeManagerService, DirectNode directNode) {
        this.iDirectNodeManagerService = iDirectNodeManagerService;
        this.directNode = directNode;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
//        throw new UnsupportedOperationException("not implemented yet");
//        CamelRouteServiceImpl.FlowProcess flowProcess = iDirectNodeManagerService.findRouteByDirectUri(directNode.getUri());

//        Object body = exchange.getIn().getBody();
//        if (! (body instanceof Map)) {
//            throw new IllegalArgumentException("DynamicRouterProcessor body is not a map");
//        }
//
//        Map<String, Object> bodyMap = (Map<String, Object>) body;
//        String tenantId = (String) bodyMap.get("tenantId");
//        String version = (String) bodyMap.get("version");
//        int matchCount = 0;
////        DynamicRouteContext.Expression matchedTarget = null;
////        List<DynamicRouteContext.Expression> expressions = DynamicRouteContext.get(id, tenantId);
////        for (DynamicRouteContext.Expression target : expressions) {
////            String expression = "(" + target.getExpression() + ") && tenantId == '" + tenantId + "'";
////            Object result = QlExpression.execute(bodyMap, expression);
////            if (Boolean.TRUE.equals(result)) {
////                matchedTarget = target;
////                matchCount ++;
////            }
////        }
//
//        if (matchCount != 1) {
//            throw new RuntimeException("DynamicRouterProcessor match count: " + matchCount + ", required only one");
//        }
////        if (version == null) {
////            version = matchedTarget.getVersion();
////        }
//
//        String routeId = matchedTarget.getValue();
//        Route route = camelContext.getRoute(routeId);
//        if (route == null) {
//            throw new IllegalArgumentException("DynamicRouterProcessor route not found: " + routeId);
//        }
//
//        ServiceStatus status = camelContext.getRouteController().getRouteStatus(routeId);
//        if (status != ServiceStatus.Started) {
//            throw new RuntimeException("DynamicRouterProcessor route not started: " + routeId);
//        }
//
//        exchange.getIn().setHeader("_routeCode", routeId);
    }

}
