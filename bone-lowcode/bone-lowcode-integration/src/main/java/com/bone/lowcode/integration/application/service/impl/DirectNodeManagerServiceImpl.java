package com.bone.lowcode.integration.application.service.impl;

import com.bone.lowcode.integration.application.service.IDirectNodeManagerService;
import com.bone.lowcode.integration.enums.EnvEnum;
import com.bone.lowcode.integration.flow.node.DirectNode;
import com.bone.lowcode.integration.flow.node.GraphNode;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DirectNodeManagerServiceImpl implements IDirectNodeManagerService {

    private final Map<String, CamelRouteServiceImpl.FlowProcess> devDirectUriContext = new HashMap<>();
    private final Map<String, CamelRouteServiceImpl.FlowProcess> prodDirectUriContext = new HashMap<>();

    @Override
    public void processDirectNode(EnvEnum env, CamelRouteServiceImpl.FlowProcess flowProcess) {
        List<GraphNode> nextNodes = flowProcess.getStartNode().getOutgoingNodes();
        if (CollectionUtils.isEmpty(nextNodes)) {
            return;
        }

        GraphNode nextNode = nextNodes.get(0);
        if (nextNode instanceof DirectNode) {
            DirectNode directNode = (DirectNode) nextNode;
            if (env == EnvEnum.PROD) {
                prodDirectUriContext.put("direct:"+directNode.getUri(), flowProcess);
            } else {
                devDirectUriContext.put("direct:"+directNode.getUri(), flowProcess);
            }
        }
    }

    /**
     * todo： 增加应用隔离
     * @param env
     * @param directUri
     * @return
     */
    @Override
    public CamelRouteServiceImpl.FlowProcess findRouteByDirectUri(EnvEnum env, String directUri) {
        if (env == EnvEnum.PROD) {
            return prodDirectUriContext.get(directUri);
        } else {
            return devDirectUriContext.get(directUri);
        }
    }
}
