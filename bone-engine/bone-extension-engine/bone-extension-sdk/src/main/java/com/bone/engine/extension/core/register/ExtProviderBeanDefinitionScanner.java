package com.bone.engine.extension.core.register;

import com.bone.engine.extension.api.annotation.Extension;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Set;

/**
 * ExtProviderBeanDefinitionScanner
 *
 * @author renhui.trh 2023-10-30
 */
public class ExtProviderBeanDefinitionScanner extends ClassPathBeanDefinitionScanner {

    public ExtProviderBeanDefinitionScanner(BeanDefinitionRegistry registry) {
        //registry是Spring的Bean注册中心
        // false表示不使用ClassPathBeanDefinitionScanner默认的TypeFilter
        // 默认的TypeFilter只会扫描带有@Service,@Controller，@Repository，@Component注解的类
        super(registry, false);
    }

    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        addIncludeFilter(new AnnotationTypeFilter(Extension.class));
        return super.doScan(basePackages);
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        return metadata.isIndependent() && !metadata.isAnnotation() && metadata.isConcrete();
    }
}