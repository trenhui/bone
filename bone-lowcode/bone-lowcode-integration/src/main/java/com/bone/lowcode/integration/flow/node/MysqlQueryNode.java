package com.bone.lowcode.integration.flow.node;

import com.bone.lowcode.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class MysqlQueryNode extends GraphNode {
    private String url;
    private String userName;
    private String password;


    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this); // 调用访问者方法
    }
}
