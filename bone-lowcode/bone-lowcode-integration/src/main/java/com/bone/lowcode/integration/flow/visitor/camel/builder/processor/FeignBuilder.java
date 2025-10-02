package com.bone.lowcode.integration.flow.visitor.camel.builder.processor;

import com.bone.lowcode.integration.flow.node.FeignNode;
import com.bone.lowcode.integration.flow.visitor.camel.context.CamelBuilderContext;
import com.bone.lowcode.integration.processor.FeignProcessor;
import org.apache.camel.model.ProcessorDefinition;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

@Component
public class FeignBuilder extends CamelProcessorBuilder<FeignNode> {

    @Resource
    private RestTemplate restTemplate;

    @Override
    public void process(FeignNode node, CamelBuilderContext context) {
        ProcessorDefinition<?> definition = context.peekDefinition();
        FeignProcessor feignProcessor=new FeignProcessor(node, restTemplate);
        definition.process(feignProcessor);

        context.writeOutput(".process(feignProcessor)\n");
    }
}
