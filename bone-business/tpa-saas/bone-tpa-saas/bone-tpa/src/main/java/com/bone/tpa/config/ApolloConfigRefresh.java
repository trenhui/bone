package com.bone.tpa.config;

import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class ApolloConfigRefresh implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Value("${saas.auto.precheck.category.threshold:0.9}")
    private BigDecimal testBigDecimal;

    @ApolloConfigChangeListener(value="application",interestedKeyPrefixes = {"saas.","ding.alert"})
    public void refresh(ConfigChangeEvent changeEvent){
        applicationContext.publishEvent(new EnvironmentChangeEvent(changeEvent.changedKeys()));

        System.out.println("testBigDecimal: " + testBigDecimal);

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
