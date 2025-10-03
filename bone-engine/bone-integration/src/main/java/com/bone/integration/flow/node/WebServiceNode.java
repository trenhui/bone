package com.bone.integration.flow.node;

import com.bone.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WebServiceNode extends GraphNode {
    private String tns;
    private String ns;
    private String wsdlUrl;
    private String serviceName;
    private String portName;
    private String methodName;
    private String protocol;

    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this); // 调用访问者方法
    }
}
