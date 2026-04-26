package com.bone.lowcode.infra.infrastructure.common.config;

import com.bone.lowcode.infra.domain.util.MultiTreeUtil;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;


/**
 * Apollo配置: 实现添加了@ConfigurationProperties注解的bean的属性(指定前缀)的动态刷新
 * 注：通过@Value注解修饰的属性原本就能动态刷新
 */
@Slf4j
@Component
public class ApolloRefreshConfig implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Autowired
    private MultiTreeUtil multiTreeUtil;

    @ApolloConfigChangeListener(value = "application", interestedKeyPrefixes = {"sa-token.", "multiTree."})
    public void refresh(ConfigChangeEvent changeEvent) {
        applicationContext.publishEvent(new EnvironmentChangeEvent(changeEvent.changedKeys()));

        for (String key : changeEvent.changedKeys()) {
            log.info("配置项 {} 发生变化，新值: {}", key, changeEvent.getChange(key).getNewValue());
            // 根据不同的配置项执行特定逻辑

            if (key.startsWith("multiTree.treeStrList[")) {
                // 配置变化时重建树
                multiTreeUtil.loadConfigAndGetTrees();
            }
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
