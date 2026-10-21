package com.bone.aiserver;

import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.ailedger.domain.gateway.LimitLedger;
import com.bone.aiorch.application.HitlApplicationService;
import com.bone.aiorch.application.OrchestrationApplicationService;
import com.bone.aiserver.claim.ClaimService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 参考宿主的理赔装配：把产品域流程 {@link ClaimService} 接到平台自动配置装配好的编排 / 解析 / HITL 端口上。
 *
 * <p><b>为什么必须有这个类</b>：{@code ClaimController} 依赖 {@link ClaimService}，而 {@link ClaimService}
 * 是<b>产品域</b>的 流程编排——平台库按「只提供端口与能力、不拥有业务流程」的分层，不会替产品域注册它的 Bean。 缺了它，参考宿主 {@link BoneAiApplication}
 * 一起就崩：{@code ClaimController} 的构造依赖无人满足。本类就是「产品侧怎么接」的活模板： 消费方照此把自己的流程编排接到端口上即可。
 *
 * <p><b>额度内核按需注入</b>：{@link LimitLedger} 经 {@link ObjectProvider} 取，bone-ai-ledger 在类路径上即启用控额； 不在则传
 * {@code null}，{@link ClaimService} 退化为不控额（行为与接入前一致）——控额能力可插拔，不强制装配方引入。
 *
 * <p>不加重载 {@code @ConditionalOnBean}：宿主已用 {@code @EnableSqlRepositories} 显式启用 rules / trace / hitl
 * 仓储， 三个依赖必然在场；条件注解的评估顺序依赖（auto-config 与用户 config 的注册先后）反而可能让 Bean 静默缺失，
 * 把「起不来」伪装成「没这个功能」。缺依赖时干脆给出明确的启动错误更利于定位。
 */
@Configuration
public class ClaimServiceConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ClaimService claimService(
      OrchestrationApplicationService orchestration,
      DocumentParser documentParser,
      HitlApplicationService hitlService,
      ObjectProvider<LimitLedger> limitLedger) {
    return new ClaimService(
        orchestration, documentParser, hitlService, limitLedger.getIfAvailable());
  }
}
