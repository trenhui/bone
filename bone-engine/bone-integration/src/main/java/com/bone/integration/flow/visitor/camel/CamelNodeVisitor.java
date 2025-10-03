package com.bone.integration.flow.visitor.camel;


import com.bone.integration.flow.node.*;
import com.bone.integration.flow.visitor.INodeVisitor;
import com.bone.integration.flow.visitor.camel.builder.BuilderFactory;
import com.bone.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.integration.flow.visitor.camel.context.FlowContext;
import com.bone.lowcode.integration.flow.node.*;
import com.bone.integration.flow.visitor.camel.builder.CamelNodeBuilder;
import lombok.Getter;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 访问者模式，遍历图的Node节点，生成camel java dsl。
 * CamelVisitor不能复用，每遍历一个图需要新的实例。
 */
public class CamelNodeVisitor implements INodeVisitor {
    private final BuilderFactory builderFactory;
    @Getter
    private final CamelBuilderContext context;
    private final Set<GraphNode> visitedNodes = new HashSet<>();

    public CamelNodeVisitor(CamelContext camelContext, FlowContext flowContext, RouteBuilder routeBuilder, BuilderFactory builderFactory) {
        this.builderFactory = builderFactory;
        context = new CamelBuilderContext();
        context.setCamelContext(camelContext);
        context.setFlowContext(flowContext);
        context.setRouteBuilder(routeBuilder);
    }

    @Override
    public void visit(StartNode startNode) {
        if (visitedNodes.contains(startNode)) return;
        visitedNodes.add(startNode);

        CamelNodeBuilder<StartNode> camelNodeBuilder = builderFactory.getBuilder(StartNode.class);
        camelNodeBuilder.process(startNode, context);

        for (GraphNode outgoingNode : startNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(NettyHttpNode httpRequestNode) {
        if (visitedNodes.contains(httpRequestNode)) return;
        visitedNodes.add(httpRequestNode);

        CamelNodeBuilder<NettyHttpNode> camelNodeBuilder = builderFactory.getBuilder(NettyHttpNode.class);
        camelNodeBuilder.process(httpRequestNode, context);

        for (GraphNode outgoingNode : httpRequestNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(MysqlQueryNode mysqlQueryNode) {
        if (visitedNodes.contains(mysqlQueryNode)) return;
        visitedNodes.add(mysqlQueryNode);
    }

    @Override
    public void visit(ChoiceNode choiceNode) {
        if (visitedNodes.contains(choiceNode)) return;
        visitedNodes.add(choiceNode);

        CamelNodeBuilder<ChoiceNode> camelNodeBuilder = builderFactory.getBuilder(ChoiceNode.class);

        camelNodeBuilder.process(choiceNode, context);

        List<WhenNode> whenNodeList = choiceNode.getWhenNodeList();
        if (whenNodeList != null) {
            for (WhenNode whenNode : whenNodeList) {
                whenNode.accept(this);
            }
        }

        ElseNode elseNode = choiceNode.getElseNode();
        if (elseNode != null) {
            elseNode.accept(this);
        }

        camelNodeBuilder.leave(choiceNode, context);
        for (GraphNode outgoingNode : choiceNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(EndNode endNode) {
        CamelNodeBuilder<EndNode> camelNodeBuilder = builderFactory.getBuilder(EndNode.class);
        camelNodeBuilder.process(endNode, context);
    }

    @Override
    public void visit(WhenNode whenNode) {
        if (visitedNodes.contains(whenNode)) return;
        visitedNodes.add(whenNode);

        CamelNodeBuilder<WhenNode> camelNodeBuilder = builderFactory.getBuilder(WhenNode.class);
        camelNodeBuilder.process(whenNode, context);

        for (GraphNode outgoingNode : whenNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(ElseNode elseNode) {
        if (visitedNodes.contains(elseNode)) return;
        visitedNodes.add(elseNode);

        CamelNodeBuilder<ElseNode> camelNodeBuilder = builderFactory.getBuilder(ElseNode.class);
        camelNodeBuilder.process(elseNode, context);

        for (GraphNode outgoingNode : elseNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(LogNode logNode) {
        if (visitedNodes.contains(logNode)) return;
        visitedNodes.add(logNode);

        CamelNodeBuilder<LogNode> camelNodeBuilder = builderFactory.getBuilder(LogNode.class);
        camelNodeBuilder.process(logNode, context);

        for (GraphNode outgoingNode : logNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(MethodNode methodNode) {
        if (visitedNodes.contains(methodNode)) return;
        visitedNodes.add(methodNode);
    }

    @Override
    public void visit(DirectNode directNode) {
        if (visitedNodes.contains(directNode)) return;
        visitedNodes.add(directNode);

        CamelNodeBuilder<DirectNode> camelNodeBuilder = builderFactory.getBuilder(DirectNode.class);
        camelNodeBuilder.process(directNode, context);

        for (GraphNode outgoingNode : directNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(TransformNode transformNode) {
        if (visitedNodes.contains(transformNode)) return;
        visitedNodes.add(transformNode);

        CamelNodeBuilder<TransformNode> camelNodeBuilder = builderFactory.getBuilder(TransformNode.class);
        camelNodeBuilder.process(transformNode, context);

        for (GraphNode outgoingNode : transformNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(HttpNode httpNode) {
        if (visitedNodes.contains(httpNode)) return;
        visitedNodes.add(httpNode);

        CamelNodeBuilder<HttpNode> camelNodeBuilder = builderFactory.getBuilder(HttpNode.class);
        camelNodeBuilder.process(httpNode, context);

        for (GraphNode outgoingNode : httpNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(ConvertBodyToNode convertBodyNode) {
        if (visitedNodes.contains(convertBodyNode)) return;
        visitedNodes.add(convertBodyNode);

        CamelNodeBuilder<ConvertBodyToNode> camelNodeBuilder = builderFactory.getBuilder(ConvertBodyToNode.class);
        camelNodeBuilder.process(convertBodyNode, context);

        for (GraphNode outgoingNode : convertBodyNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(SetHeaderNode setHeaderNode) {
        if (visitedNodes.contains(setHeaderNode)) return;
        visitedNodes.add(setHeaderNode);

        CamelNodeBuilder<SetHeaderNode> camelNodeBuilder = builderFactory.getBuilder(SetHeaderNode.class);
        camelNodeBuilder.process(setHeaderNode, context);

        for (GraphNode outgoingNode : setHeaderNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(UnmarshalNode unmarshalNode) {
        if (visitedNodes.contains(unmarshalNode)) return;
        visitedNodes.add(unmarshalNode);

        CamelNodeBuilder<UnmarshalNode> camelNodeBuilder = builderFactory.getBuilder(UnmarshalNode.class);
        camelNodeBuilder.process(unmarshalNode, context);

        for (GraphNode outgoingNode : unmarshalNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(MarshalNode marshalNode) {
        if (visitedNodes.contains(marshalNode)) return;
        visitedNodes.add(marshalNode);

        CamelNodeBuilder<MarshalNode> camelNodeBuilder = builderFactory.getBuilder(MarshalNode.class);
        camelNodeBuilder.process(marshalNode, context);

        for (GraphNode outgoingNode : marshalNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(DynamicRouteNode dynamicRouteNode) {
        if (visitedNodes.contains(dynamicRouteNode)) return;
        visitedNodes.add(dynamicRouteNode);

        CamelNodeBuilder<DynamicRouteNode> camelNodeBuilder = builderFactory.getBuilder(DynamicRouteNode.class);
        camelNodeBuilder.process(dynamicRouteNode, context);

        for (GraphNode outgoingNode : dynamicRouteNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(RecipientListNode recipientListNode) {
        if (visitedNodes.contains(recipientListNode)) return;
        visitedNodes.add(recipientListNode);

        CamelNodeBuilder<RecipientListNode> camelNodeBuilder = builderFactory.getBuilder(RecipientListNode.class);
        camelNodeBuilder.process(recipientListNode, context);

        for (GraphNode outgoingNode : recipientListNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(WebServiceNode webServiceNode) {
        if (visitedNodes.contains(webServiceNode)) return;
        visitedNodes.add(webServiceNode);

        CamelNodeBuilder<WebServiceNode> camelNodeBuilder = builderFactory.getBuilder(WebServiceNode.class);
        camelNodeBuilder.process(webServiceNode, context);

        for (GraphNode outgoingNode : webServiceNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    //加解密
    @Override
    public void visit(EnAndDeNode enAndDeNode) {
        if (visitedNodes.contains(enAndDeNode)) return;
        visitedNodes.add(enAndDeNode);

        CamelNodeBuilder<EnAndDeNode> camelNodeBuilder = builderFactory.getBuilder(EnAndDeNode.class);
        camelNodeBuilder.process(enAndDeNode, context);

        for (GraphNode outgoingNode : enAndDeNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(YingDaEncryptNode yingDaEncryptNode) {
        if (visitedNodes.contains(yingDaEncryptNode)) return;
        visitedNodes.add(yingDaEncryptNode);

        CamelNodeBuilder<YingDaEncryptNode> camelNodeBuilder = builderFactory.getBuilder(YingDaEncryptNode.class);
        camelNodeBuilder.process(yingDaEncryptNode, context);

        for (GraphNode outgoingNode : yingDaEncryptNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(FeignNode feignNode) {
        if (visitedNodes.contains(feignNode)) return;
        visitedNodes.add(feignNode);

        CamelNodeBuilder<FeignNode> camelNodeBuilder = builderFactory.getBuilder(FeignNode.class);
        camelNodeBuilder.process(feignNode, context);

        for (GraphNode outgoingNode : feignNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }
    @Override
    public void visit(ScriptNode scriptNode) {
        if (visitedNodes.contains(scriptNode)) return;
        visitedNodes.add(scriptNode);

        CamelNodeBuilder<ScriptNode> camelNodeBuilder = builderFactory.getBuilder(ScriptNode.class);
        camelNodeBuilder.process(scriptNode, context);

        for (GraphNode outgoingNode : scriptNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(SetHeadersNode setHeadersNode) {
        if (visitedNodes.contains(setHeadersNode)) return;
        visitedNodes.add(setHeadersNode);

        CamelNodeBuilder<SetHeadersNode> camelNodeBuilder = builderFactory.getBuilder(SetHeadersNode.class);
        camelNodeBuilder.process(setHeadersNode, context);

        for (GraphNode outgoingNode : setHeadersNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }

    @Override
    public void visit(SetBodyNode setBodyNode) {
        if (visitedNodes.contains(setBodyNode)) return;
        visitedNodes.add(setBodyNode);

        CamelNodeBuilder<SetBodyNode> camelNodeBuilder = builderFactory.getBuilder(SetBodyNode.class);
        camelNodeBuilder.process(setBodyNode, context);

        for (GraphNode outgoingNode : setBodyNode.getOutgoingNodes()) {
            outgoingNode.accept(this);
        }
    }


}
