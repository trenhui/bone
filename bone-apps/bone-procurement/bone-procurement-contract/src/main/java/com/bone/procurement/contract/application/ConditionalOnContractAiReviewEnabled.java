package com.bone.procurement.contract.application;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 合同 AI 审查能力的装配条件（组合注解）。
 *
 * <p>等价于 {@code @ConditionalOnProperty(prefix="bone.procurement.contract-ai-review",
 * name="enabled", havingValue="true")}；应用服务与 Controller 共用同一条件，避免两处开关漂移。{@code
 * matchIfMissing=false} 保证缺省即关闭。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(
    prefix = ContractAiReviewProperties.PREFIX,
    name = "enabled",
    havingValue = "true")
public @interface ConditionalOnContractAiReviewEnabled {}
