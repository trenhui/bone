package com.bone.lowcode.integration.flow.visitor.camel.builder.processor;

import com.bone.lowcode.integration.flow.node.SetHeadersNode;
import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;
import static org.apache.camel.builder.Builder.simple;
import java.util.Map;

@Component
public class SetHeadersBuilder extends CamelProcessorBuilder<SetHeadersNode> {

    @Override
    public void process(SetHeadersNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();

        // 遍历 headers 列表
        for (Map<String, String> headerMap : node.getHeaders()) {
                // 设置每个 header
                definition.setHeader(headerMap.get("headerName"), simple(headerMap.get("headerValue")));

                // 记录输出代码
                context.writeOutput(".setHeader('")
                        .writeOutput(headerMap.get("headerName"))
                        .writeOutput("', simple('")
                        .writeOutput(headerMap.get("headerValue"))
                        .writeOutput("'))\n");
            }
        }
    }

