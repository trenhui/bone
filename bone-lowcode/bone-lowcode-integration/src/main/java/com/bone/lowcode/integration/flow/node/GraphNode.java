package com.bone.lowcode.integration.flow.node;

import com.alibaba.fastjson.annotation.JSONField;
import com.bone.lowcode.integration.flow.visitor.INodeVisitor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 基础图节点
@Data
public abstract class GraphNode {
    @JSONField(name = "key")
    private String id;
    private String name;
    private List<GraphNode> incomingNodes = new ArrayList<>();
    private List<GraphNode> outgoingNodes = new ArrayList<>();
    private List<String> incomings = new ArrayList<>();
    private List<String> outgoings = new ArrayList<>();


    public void addIncomingNode(GraphNode node) {
        if (incomingNodes == null) {
            incomingNodes = new ArrayList<GraphNode>();
        }

        incomingNodes.add(node);
    }

    public void addOutgoingNode(GraphNode node) {
        if (outgoingNodes == null) {
            outgoingNodes = new ArrayList<>();
        }

        outgoingNodes.add(node);
    }




    public abstract void accept(INodeVisitor visitor);
}



