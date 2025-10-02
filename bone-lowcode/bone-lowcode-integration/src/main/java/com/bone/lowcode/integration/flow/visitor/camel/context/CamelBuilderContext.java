package com.bone.lowcode.integration.flow.visitor.camel.context;

import lombok.Data;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

@SuppressWarnings("unchecked")
@Data
public class CamelBuilderContext {
    private RouteBuilder routeBuilder;
    private CamelContext camelContext;
    private FlowContext flowContext;
    private StringBuilder stringBuilder = new StringBuilder();
    private Stack<ProcessorDefinition<?>> definitionStack = new Stack<>();

    private Map<String, Object> properties = new HashMap<>();

    public void addProperty(String key, Object value) {
        properties.put(key, value);
    }

    public <T> T getProperty(String key) {
        return (T) properties.get(key);
    }

    public void pushDefinition(ProcessorDefinition<?> processorDefinition) {
        definitionStack.push(processorDefinition);
    }

    public ProcessorDefinition<?> popDefinition() {
        return definitionStack.pop();
    }

    public <T extends ProcessorDefinition<T>> T peekDefinition() {
        return (T) definitionStack.peek();
    }

    public CamelBuilderContext writeOutput(String dsl) {
        stringBuilder.append(dsl);
        return this;
    }

    public String getOutput() {
        return stringBuilder.toString();
    }
}
