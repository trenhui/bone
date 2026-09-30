package com.bone.blueprint.domain.gateway;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 定价规则网关（领域出站端口）：消费 bone-metadata-server 的「定价规则中心」动态记录。
 *
 * <p><b>业界场景</b>：营销定价规则（VIP 几折、企业客户几折、大促满减）是典型的<strong>运营配置数据</strong>—— 变更频率远高于代码发版，且要求改完即生效。Bone
 * 平台的对应能力是元数据建模（PRICING_RULE 实体）+ 动态记录 CRUD（模式 B RUNTIME
 * API）：运营在元数据前端建模/发布/维护记录，扩展点实现按场景编码实时读取，无需发版。
 *
 * <p>与 {@link MasterDataGateway#findLevelDiscountRate(String)} 的分工：masterdata
 * 承载<strong>治理型主数据</strong> （客户等级目录及其标准折扣，变更走治理流程）；metadata-server 承载<strong>运营型规则</strong>（随时调整的
 * 营销折扣，改完即生效）。计价时规则中心优先、主数据兜底、本地常量最后。
 *
 * <p><b>降级约定</b>：与 {@link MasterDataGateway} 一致——服务不可达返回 empty 并告警，调用方走下一级兜底。
 */
public interface PricingRuleGateway {

  /**
   * 按定价场景编码查生效折扣率。
   *
   * @param scenario 定价场景编码（与扩展点路由 scenario 一致：vip / member / enterprise / promotion）
   * @return 折扣率（0.85 表示 85 折）；empty=规则中心无该场景的启用规则，或规则中心不可达（实现方需告警）
   */
  Optional<BigDecimal> findScenarioDiscountRate(String scenario);
}
