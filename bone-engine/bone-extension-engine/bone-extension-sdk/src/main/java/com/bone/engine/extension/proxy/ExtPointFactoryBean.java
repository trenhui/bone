package com.bone.engine.extension.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * ExtPointFactoryBean
 *
 * @author renhui.trh 2023-10-30
 */
public class ExtPointFactoryBean<T> implements FactoryBean<T>, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(ExtPointFactoryBean.class);
    private final Class<T> extPoint;
    private final Map<String, Object> attrs;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("ExtPointFactoryBean afterPropertiesSet");
    }

    public ExtPointFactoryBean(Class<T> extPoint, Map<String, Object> attrs) {
        this.extPoint = extPoint;
        this.attrs = attrs;
    }

    @Override
    public T getObject() throws Exception {
        return (T) Proxy.newProxyInstance(this.extPoint.getClassLoader(), new Class[]{this.extPoint}, new ExtPointProxy(extPoint, attrs));
    }

    @Override
    public Class<?> getObjectType() {
        return this.extPoint;
    }
}
