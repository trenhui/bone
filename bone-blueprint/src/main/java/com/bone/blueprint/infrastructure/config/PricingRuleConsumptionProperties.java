package com.bone.blueprint.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 定价规则中心消费配置（bone-blueprint → bone-metadata-server ACL）。
 *
 * <p>实体编码是元数据建模时确定的约定值（PRICING_RULE），跨环境不变；服务账号用于登录网关取 token。 走统一网关（与 {@link
 * MasterDataConsumptionProperties} 同构），登录后携 Bearer token 访问。
 */
@Data
@Component
@ConfigurationProperties(prefix = "bone.pricing-rule")
public class PricingRuleConsumptionProperties {

  /** 是否启用定价规则中心（false 时跳过查询，直接走 masterdata / 本地兜底）。 */
  private boolean enabled = true;

  /** 元数据服务网关地址（登录与动态记录 API 同源）。 */
  private String baseUrl = "http://localhost:8888";

  /** 服务间登录账号（样板工程默认复用租户管理员）。 */
  private String serviceUsername = "tenant_admin";

  /** 服务间登录密码。 */
  private String servicePassword = "123456";

  /**
   * 服务账号所属租户（登录时以 X-Tenant-Id 头声明）。
   *
   * <p><b>为何需要</b>：metadata-server 的 RUNTIME 记录 API 以 <strong>token 租户</strong>解析数据归属，
   * 请求头不改变数据面租户——规则中心数据建在哪个租户，服务账号就必须是该租户的账号。
   */
  private Long serviceTenantId = 1001L;

  /** 定价规则实体编码（元数据建模约定，租户 1001 已发布 RUNTIME）。 */
  private String entityCode = "PRICING_RULE";

  /** 记录中承载场景编码的字段名。 */
  private String scenarioField = "scenario";

  /** 记录中承载折扣率的字段名。 */
  private String rateField = "discount_rate";

  /** 记录中承载启停状态的字段名（可选）。 */
  private String enabledField = "enabled";

  /** 查询结果本地缓存秒数（避免每单都打元数据服务）。 */
  private long cacheTtlSeconds = 30;

  /** 单次拉取记录的页大小上限。 */
  private int fetchSize = 200;
}
