package com.bone.engine.extension.core.proxy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * ExtensionPointFactoryBean
 *
 * @author renhui.trh 2023-10-30
 */
@Slf4j
public class ExtensionPointFactoryBean<T> implements FactoryBean<T>, InitializingBean//, ApplicationContextAware, BeanFactoryAware {
{
    private final Class<T> extPoint;
    private final Map<String, Object> attrs;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("ExtensionPointFactoryBean afterPropertiesSet");
    }

    public ExtensionPointFactoryBean(Class<T> extPoint, Map<String, Object> attrs) {
        this.extPoint = extPoint;
        this.attrs = attrs;
    }

    @Override
    public T getObject() throws Exception {
        return (T) Proxy.newProxyInstance(this.extPoint.getClassLoader(), new Class[]{this.extPoint}, new ExtensionPointProxy(extPoint, attrs));
    }

    @Override
    public Class<?> getObjectType() {
        return this.extPoint;
    }
}
