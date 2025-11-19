package com.bone.engine.extension.core.register;

import com.bone.engine.extension.api.annotation.EnableExtPoints;
import com.bone.engine.extension.api.annotation.ExtPoint;
import com.bone.engine.extension.core.proxy.ExtPointFactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * ExtPointRegistrar
 *
 * @author renhui.trh 2023-10-30
 */
public final class ExtPointRegister implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {
    private ResourceLoader resourceLoader;
    private Environment environment;

    ExtPointRegister() {
    }

    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        this.registerExtPoints(metadata, registry);
        this.registerExtProviders(metadata, registry);
    }

    private void registerExtProviders(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        ExtProviderBeanDefinitionScanner scanner = new ExtProviderBeanDefinitionScanner(registry);
        scanner.scan(getBasePackages(metadata));
    }

    public void registerExtPoints(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        LinkedHashSet<BeanDefinition> candidateComponents = new LinkedHashSet();
        ClassPathScanningCandidateComponentProvider scanner = this.getScanner();
        scanner.setResourceLoader(this.resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ExtPoint.class));
        String[] basePackages = this.getBasePackages(metadata);
        for (String basePackage : basePackages) {
            candidateComponents.addAll(scanner.findCandidateComponents(basePackage));
        }

        Map<String, Object> attrs = metadata.getAnnotationAttributes(EnableExtPoints.class.getCanonicalName());
        registerBeanDefinition(metadata, registry, attrs, candidateComponents);
    }

    private void registerBeanDefinition(AnnotationMetadata metadata, BeanDefinitionRegistry registry, Map<String, Object> attrs, LinkedHashSet<BeanDefinition> candidateComponents) {

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


    protected ClassPathScanningCandidateComponentProvider getScanner() {
        return new ClassPathScanningCandidateComponentProvider(false, this.environment) {
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                boolean isCandidate = false;
                if (beanDefinition.getMetadata().isIndependent() && !beanDefinition.getMetadata().isAnnotation()) {
                    isCandidate = true;
                }
                return isCandidate;
            }
        };
    }

    protected String[] getBasePackages(AnnotationMetadata importingClassMetadata) {
        Map<String, Object> attributes = importingClassMetadata.getAnnotationAttributes(EnableExtPoints.class.getCanonicalName());
        Set<String> basePackages = new HashSet();
        String[] basePackagesArr = (String[]) ((String[]) attributes.get("basePackages"));
        for (String item : basePackagesArr) {
            if (StringUtils.hasText(item))
                basePackages.add(item);
        }
        if (basePackages.isEmpty()) {
            basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }

        return basePackages.toArray(new String[basePackages.size()]);
    }


    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
