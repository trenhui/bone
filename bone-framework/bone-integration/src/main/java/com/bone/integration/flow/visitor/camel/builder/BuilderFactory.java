package com.bone.integration.flow.visitor.camel.builder;


import com.bone.integration.flow.node.GraphNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class BuilderFactory {
    private final static Map<Class<? extends GraphNode>, Class<? extends CamelNodeBuilder>> builderClassMap = new HashMap<>();
    private final static Map<Class<? extends GraphNode>, CamelNodeBuilder> builderBeanMap = new HashMap<>();

    @Resource
    private ApplicationContext applicationContext;

    @PostConstruct
    public void scanBuilderClass() {
        Reflections reflections = new Reflections("com.bone.lowcode.integration.flow.visitor.camel.builder");

        Set<Class<? extends CamelNodeBuilder>> builderClasses = reflections.getSubTypesOf(CamelNodeBuilder.class);

        for (Class<? extends CamelNodeBuilder> subclass : builderClasses) {
            Class<? extends GraphNode> graphNodeClass = getGenericType(subclass);
            if (graphNodeClass == null) {
                continue;
            }

            builderClassMap.put(graphNodeClass, subclass);
            log.info("scan camel builder class: {} -> {}", graphNodeClass, subclass);
        }
    }

    /**
     * 当所有bean初始化完成之后，将node对应的builder放入map中
     * @throws Exception
     */
    public void runAfterApplicationStarted() throws Exception {
        for (Map.Entry<Class<? extends GraphNode>, Class<? extends CamelNodeBuilder>> entry : builderClassMap.entrySet()) {
            CamelNodeBuilder camelNodeBuilder = applicationContext.getBean(entry.getValue());
            log.info("registering camel builder: {} -> {}", entry.getKey(), camelNodeBuilder);
            builderBeanMap.put(entry.getKey(), camelNodeBuilder);
        }
    }

    public <T extends GraphNode> CamelNodeBuilder<T> getBuilder(Class<T> clazz) {
        CamelNodeBuilder<T> camelNodeBuilder = builderBeanMap.get(clazz);
        if (camelNodeBuilder == null) {
            throw new IllegalArgumentException("Unknown builder type: " + clazz);
        }

        return camelNodeBuilder;
    }


    public static Class<? extends GraphNode> getGenericType(Class<?> clazz) {
        java.lang.reflect.Type genericSuperclass = clazz.getGenericSuperclass();
        if (genericSuperclass instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.ParameterizedType type = (java.lang.reflect.ParameterizedType) genericSuperclass;
            java.lang.reflect.Type[] typeArgs = type.getActualTypeArguments();
            if (typeArgs.length > 0) {
                return (Class<? extends GraphNode>) typeArgs[0];
            }
        }

        return null;
    }

    public static void main(String[] args) {
        new BuilderFactory().scanBuilderClass();
        System.out.println(builderClassMap);
    }

}
