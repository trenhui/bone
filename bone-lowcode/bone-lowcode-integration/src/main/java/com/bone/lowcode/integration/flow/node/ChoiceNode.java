package com.bone.lowcode.integration.flow.node;

import com.bone.lowcode.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChoiceNode extends GraphNode {
    private String url;
    private String method;
    private List<WhenNode> whenNodeList;
    private ElseNode elseNode;


    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this); // 调用访问者方法
    }

    public void addWhenNode(WhenNode whenNode) {
        if (this.whenNodeList == null) {
            whenNodeList = new ArrayList<>();
        }

        whenNodeList.add(whenNode);
    }
}
