package com.bone.integration.flow.visitor;


import com.bone.integration.flow.node.*;

/**
 * 定义访问者接口
 */
public interface INodeVisitor {
    void visit(StartNode startNode);
    void visit(DirectNode marshalNode);
    void visit(NettyHttpNode httpRequestNode);
    void visit(MysqlQueryNode mysqlQueryNode);
    void visit(ChoiceNode choiceNode);
    void visit(EndNode endNode);
    void visit(WhenNode whenNode);
    void visit(ElseNode elseNode);
    void visit(LogNode logNode);
    void visit(MethodNode methodNode);
    void visit(TransformNode transformNode);
    void visit(HttpNode httpNode);
    void visit(ConvertBodyToNode convertBodyNode);
    void visit(SetHeaderNode setHeaderNode);
    void visit(UnmarshalNode unmarshalNode);
    void visit(MarshalNode marshalNode);
    void visit(DynamicRouteNode dynamicRouteNode);
    void visit(RecipientListNode recipientListNode);
    void visit(WebServiceNode webServiceNode);
    void visit(EnAndDeNode enAndDeNode);
    void visit(YingDaEncryptNode yingDaEncryptNode);
    void visit(FeignNode feignNode);
    void visit(ScriptNode scriptNode);
    void visit(SetHeadersNode setHeadersNode);
    void visit(SetBodyNode setBodyNode);

}

