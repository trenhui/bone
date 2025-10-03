package com.bone.integration.flow.node;

import com.bone.integration.flow.visitor.INodeVisitor;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class FeignNode extends GraphNode {
    @NotEmpty(message = "服务名不能为空")
    private String serviceName;//服务名

    @NotEmpty(message = "路径不能为空")
    private String path;//路径

    @NotEmpty(message = "key和value不能为空")
    private List<Map<String, String>> parameters;//入参

    @NotEmpty(message = "请求方式不能为空")
    private String method;//请求方式

    @NotEmpty(message = "报文格式为空")
    private String contextType;//报文格式

    private String body;


    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this); // 调用访问者方法
    }
}
