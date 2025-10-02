package com.bone.core.extension.proxy;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;

import java.util.LinkedHashSet;
import java.util.Map;

/**
 * ExtPointProxyBeanBuilder
 *
 * @author renhui.trh 2023-10-30
 */
public class ExtPointProxyBeanBuilder {

    public static void registerBeanDefinition(AnnotationMetadata metadata, BeanDefinitionRegistry registry, Map<String, Object> attrs, LinkedHashSet<BeanDefinition> candidateComponents) {

        for (BeanDefinition candidateComponent : candidateComponents) {
            GenericBeanDefinition beanDefinition = (GenericBeanDefinition) candidateComponent;//.getBeanDefinition());
            //将bean的真实类型改变为FactoryBean
            beanDefinition.getConstructorArgumentValues().
                    addGenericArgumentValue(beanDefinition.getBeanClassName());
            beanDefinition.getConstructorArgumentValues()
                    .addGenericArgumentValue(attrs);
            beanDefinition.setBeanClass(ExtPointFactoryBean.class);
            beanDefinition.setPrimary(true);
            beanDefinition.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_TYPE);

            if (candidateComponent instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
                AnnotationMetadata annotationMetadata = annotatedBeanDefinition.getMetadata();
                Assert.isTrue(annotationMetadata.isInterface(), "@ExtPoint can only be specified on an interface");
                String className = annotationMetadata.getClassName();
                BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className);
                BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
            }
        }
    }
}
