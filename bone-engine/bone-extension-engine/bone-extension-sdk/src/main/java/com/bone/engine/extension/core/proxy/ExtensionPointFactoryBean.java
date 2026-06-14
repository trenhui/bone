package com.bone.engine.extension.core.proxy;

import java.lang.reflect.Proxy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/** 扩展点代理工厂 Bean 通过 ImportBeanDefinitionRegistrar 动态注册 */
@Slf4j
public class ExtensionPointFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware {

  private final Class<T> extensionPointClass;
  private ApplicationContext applicationContext;

  /** 关键构造函数！ 被 ExtensionPointRegister 通过 addIndexedArgumentValue(0, Class对象) 调用 */
  public ExtensionPointFactoryBean(Class<T> extensionPointClass) {
    this.extensionPointClass = extensionPointClass;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public T getObject() throws Exception {
    log.debug("Creating dynamic proxy for ExtensionPoint: {}", extensionPointClass.getName());
    return (T)
        Proxy.newProxyInstance(
            extensionPointClass.getClassLoader(),
            new Class<?>[] {extensionPointClass},
            new ExtensionPointProxy<>(extensionPointClass, applicationContext));
  }

  @Override
  public Class<?> getObjectType() {
    return extensionPointClass;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
