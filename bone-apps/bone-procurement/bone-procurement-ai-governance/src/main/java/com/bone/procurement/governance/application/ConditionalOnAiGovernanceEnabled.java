package com.bone.procurement.governance.application;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 治理能力装配条件：应用服务与 Controller 共用以避免开关漂移；matchIfMissing=false 缺省关闭。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(
    prefix = AiGovernanceProperties.PREFIX,
    name = "enabled",
    havingValue = "true")
public @interface ConditionalOnAiGovernanceEnabled {}
