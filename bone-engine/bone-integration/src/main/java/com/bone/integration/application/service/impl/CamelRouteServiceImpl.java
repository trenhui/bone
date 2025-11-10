package com.bone.integration.application.service.impl;

import com.bone.integration.application.service.ICamelRouteService;
import com.bone.integration.application.service.IDirectNodeManagerService;
import com.bone.integration.application.service.IRouteManageService;
import com.bone.integration.domain.model.FlowDefinitionDO;
import com.bone.integration.domain.model.FlowVersionDO;
import com.bone.integration.domain.repository.IFlowDefinitionRepository;
import com.bone.integration.domain.repository.IFlowVersionRepository;
import com.bone.integration.enums.EnvEnum;
import com.bone.integration.enums.FlowTypeEnum;
import com.bone.integration.flow.convertor.FlowSchemaConvertor;
import com.bone.integration.flow.node.StartNode;
import com.bone.integration.flow.visitor.camel.builder.BuilderFactory;
import com.bone.integration.route.DevCamelContextManager;
import com.bone.integration.utils.RouteUtils;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.bone.integration.common.Constants.DEV_VERSION;

@Component
@Slf4j
@DependsOn("devCamelContextManager")
public class CamelRouteServiceImpl implements ICamelRouteService {

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
    @Resource
    private IRouteManageService iRouteManageService;
    @Resource
    private IDirectNodeManagerService iDirectNodeManagerService;

    @Override
    public void buildRouteByFlowId(Long flowId, String env) {
        try {
            addCamelRoute(findStartNode(flowId), env);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void buildRouteByFlowVersionId(Long flowVersionId) {
        try {
            addCamelRoute(findStartNodeForProd(flowVersionId), EnvEnum.PROD.name());
        } catch (Exception e) {
            throw new RuntimeException("buildRoute error, flowId: " + flowVersionId, e);
        }
    }

    @Override
    public void stopRouteByFlowVersionId(Long flowVersionId) {
        FlowVersionDO flowVersionDO = iFlowVersionRepository.getById(flowVersionId);
        if (flowVersionDO == null) {
            return;
        }

        String routId = getRoutId(flowVersionDO.getFlowKey(),
                flowVersionDO.getFlowType(), flowVersionDO.getFlowVersion());
        iRouteManageService.stopRoute(routId);
    }

    private String getRoutId(String flowKey, String flowType, String version) {
        if (flowType.equals(FlowTypeEnum.MULTI_VERSION.getCode())) {
            return flowKey + "_" + version;
        } else {
            return flowKey;
        }
    }

    private FlowProcess findStartNode(Long flowId) {
        FlowDefinitionDO flowDefinitionDO = iFlowDefinitionRepository.getById(flowId);
        Assert.notNull(flowDefinitionDO, "flow definition not found");
        StartNode startNode = FlowSchemaConvertor.buildGraphNode(flowDefinitionDO.getFlowContent());
        return new FlowProcess().setAppCode(flowDefinitionDO.getAppCode()).setStartNode(startNode).setFlowKey(flowDefinitionDO.getFlowKey())
                .setFlowType(FlowTypeEnum.getByCode(flowDefinitionDO.getFlowType())).setVersion(DEV_VERSION);
    }

    private FlowProcess findStartNodeForProd(Long flowVersionId) {
        FlowVersionDO flowVersionDO = iFlowVersionRepository.getById(flowVersionId);
        Assert.notNull(flowVersionDO, "flow version not found: " + flowVersionId);
        StartNode startNode = FlowSchemaConvertor.buildGraphNode(flowVersionDO.getFlowContent());
        return new FlowProcess().setAppCode(flowVersionDO.getAppCode()).setStartNode(startNode).setFlowKey(flowVersionDO.getFlowKey())
                .setFlowType(FlowTypeEnum.getByCode(flowVersionDO.getFlowType())).setVersion(flowVersionDO.getFlowVersion());
    }

    @Override
    public void buildAllCamelFlow() {
        buildCamelDevFlows();
        buildCamelProdFlows();
    }

    private void buildCamelDevFlows() {
        FlowDefinitionDO query = new FlowDefinitionDO();
        List<FlowDefinitionDO> flowDefinitionDOList = iFlowDefinitionRepository.query(query);

        List<FlowProcess> flowProcessList = new ArrayList<>();
        for (FlowDefinitionDO flowDefinitionDO : flowDefinitionDOList) {
            try {
                StartNode startNode = FlowSchemaConvertor.buildGraphNode(flowDefinitionDO.getFlowContent());
                flowProcessList.add(new FlowProcess().setAppCode(flowDefinitionDO.getAppCode()).setStartNode(startNode).setFlowKey(flowDefinitionDO.getFlowKey())
                        .setFlowType(FlowTypeEnum.getByCode(flowDefinitionDO.getFlowType())).setVersion(DEV_VERSION));
            } catch (Exception e) {
                log.error("buildCamelDevFlows error", e);
            }
        }

        try {
            addCamelRoutes(flowProcessList, EnvEnum.DEV.name(), false);
        } catch (Exception e) {
            throw new RuntimeException("init camel flow error", e);
        }
    }

    private void buildCamelProdFlows() {
        FlowVersionDO query = new FlowVersionDO();
        List<FlowVersionDO> flowDefinitionDOList = iFlowVersionRepository.queryMaxVersion(query);

        List<FlowProcess> flowProcessList = new ArrayList<>();
        for (FlowVersionDO flowVersionDO : flowDefinitionDOList) {
            try {
                StartNode startNode = FlowSchemaConvertor.buildGraphNode(flowVersionDO.getFlowContent());
                flowProcessList.add(new FlowProcess().setAppCode(flowVersionDO.getAppCode()).setStartNode(startNode).setFlowKey(flowVersionDO.getFlowKey())
                        .setFlowType(FlowTypeEnum.getByCode(flowVersionDO.getFlowType())).setVersion(flowVersionDO.getFlowVersion()));
            } catch (Exception e) {
                log.error("buildCamelProdFlows error", e);
            }
        }

        try {
            addCamelRoutes(flowProcessList, EnvEnum.PROD.name(), false);
        } catch (Exception e) {
            throw new RuntimeException("init camel flow error", e);
        }
    }

    private void addCamelRoute(FlowProcess flowProcess, String env) throws Exception {
        addCamelRoutes(Collections.singletonList(flowProcess), env, true);
    }

    private void addCamelRoutes(List<FlowProcess> flowProcessList, String env, boolean throwException) throws Exception {
        log.info("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~{}~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~", env);
        // 全部初始化到内存中供后续使用
        for (FlowProcess flowProcess : flowProcessList) {
            iDirectNodeManagerService.processDirectNode(EnvEnum.valueOf(env), flowProcess);
        }

        for (FlowProcess flowProcess : flowProcessList) {
            try {
                iRouteManageService.addRoute(EnvEnum.valueOf(env), flowProcess);
            } catch (Exception e) {
                if (throwException) {
                    throw e;
                } else {
                    log.error("init flow error! flowKey: " + flowProcess.getFlowKey(), e);
                }
            }
        }
    }

    @Data
    @Accessors(chain = true)
    public static class FlowProcess {
        private String flowKey;
        private StartNode startNode;
        private FlowTypeEnum flowType;
        private String version;
        private String status;
        private String appCode;

        public boolean isSupportMultiVersion() {
            return FlowTypeEnum.MULTI_VERSION == flowType;
        }

        public String getRouteId() {
            return RouteUtils.getRouteId(appCode, flowKey, version, isSupportMultiVersion());
        }
    }
}