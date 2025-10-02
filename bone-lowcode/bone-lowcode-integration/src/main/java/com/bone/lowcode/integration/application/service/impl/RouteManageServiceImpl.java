package com.bone.lowcode.integration.application.service.impl;

import com.bone.lowcode.integration.application.service.IRouteManageService;
import com.bone.lowcode.integration.domain.repository.IFlowDefinitionRepository;
import com.bone.lowcode.integration.domain.repository.IFlowVersionRepository;
import com.bone.lowcode.integration.enums.EnvEnum;
import com.bone.lowcode.integration.flow.visitor.camel.CamelNodeVisitor;
import com.bone.lowcode.integration.flow.visitor.camel.builder.BuilderFactory;
import com.bone.lowcode.integration.flow.visitor.camel.context.FlowContext;
import com.bone.lowcode.integration.route.DevCamelContextManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Component
@DependsOn("devCamelContextManager")
@Slf4j
public class RouteManageServiceImpl implements IRouteManageService {

    @Resource
    private IFlowDefinitionRepository iFlowDefinitionRepository;
    @Resource
    private IFlowVersionRepository iFlowVersionRepository;
    @Resource
    private CamelContext camelContext;
    @Resource
    private BuilderFactory builderFactory;
    @Resource
    private DevCamelContextManager devCamelContextManager;
    private final Map<String, CamelRouteServiceImpl.FlowProcess> devRouteContext = new HashMap<>();
    private final Map<String, CamelRouteServiceImpl.FlowProcess> prodRouteContext = new HashMap<>();

    @Override
    public void addRoute(EnvEnum env, CamelRouteServiceImpl.FlowProcess flowProcess) throws Exception {
        CamelContext innerCamelContext = getCamelContext(env);
        innerCamelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                FlowContext flowContext = new FlowContext();
                flowContext.setAppCode(flowProcess.getAppCode());
                flowContext.setSupportMultiVersion(flowProcess.isSupportMultiVersion());
                flowContext.setVersion(flowProcess.getVersion());
                flowContext.setEnv(env);
                flowContext.setFlowKey(flowProcess.getFlowKey());
                CamelNodeVisitor camelVisitor = new CamelNodeVisitor(innerCamelContext, flowContext, this, builderFactory);
                camelVisitor.visit(flowProcess.getStartNode());
                String dsl = camelVisitor.getContext().getOutput();
                System.out.println(dsl);
            }
        });

        if (env == EnvEnum.DEV) {
            devRouteContext.put(flowProcess.getFlowKey(), flowProcess);
        } else {
            prodRouteContext.put(flowProcess.getFlowKey(), flowProcess);
        }
    }

    private CamelContext getCamelContext(EnvEnum env) {
        if (env == EnvEnum.DEV) {
            return devCamelContextManager.getCamelContext();
        } else {
            return camelContext;
        }
    }

    @Override
    public void startRoute(String routeId) {
        log.info("starting route: {}", routeId);

        try {
            getCamelContext(EnvEnum.PROD).getRouteController().startRoute(routeId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stopRoute(String routeId) {
        log.info("stopping route: {}", routeId);

        try {
            getCamelContext(EnvEnum.PROD).getRouteController().stopRoute(routeId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeRoute(String routeId) {

    }

    @Override
    public void findRoute(String routeId) {

    }
}
