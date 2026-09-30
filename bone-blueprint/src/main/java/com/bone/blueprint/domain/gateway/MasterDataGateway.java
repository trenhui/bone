package com.bone.blueprint.domain.gateway;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 主数据网关（领域出站端口）：消费 bone-masterdata 的已发布主数据。
 *
 * <p><b>为何存在</b>：下单的商品、客户等级及其折扣率，过去要么凭调用方自报（商品不存在也能下单、 价格随口报）、要么是扩展点里的硬编码常量（改折扣率要发版）。
 * 真实企业场景里，这些全是<strong>主数据</strong>——由治理流程发布、下游统一消费。本端口把
 * 「主数据长什么样、从哪取」隔离在领域之外，领域层只表达业务问题：这个商品是什么、多少钱？这个客户是什么等级？该等级打几折？
 *
 * <p><b>降级约定（样板工程口径）</b>：实现方在主数据服务<strong>不可达</strong>时应返回 {@link Optional#empty()} /
 * 「未知」语义并记录告警，调用方按本地兜底策略继续；但当主数据服务<strong>明确返回</strong>「未发布 / 未配置」时， 必须如实传达——这是治理语义，不是故障。
 */
public interface MasterDataGateway {

  /**
   * 查已发布的商品主数据记录。
   *
   * @param productCode 商品编码（订单明细的 productId 字符串形态）
   * @return 商品视图（编码/名称/单价）；empty=不存在/未发布，或主数据服务不可达（实现方需告警）
   */
  Optional<ProductView> findPublishedProduct(String productCode);

  /**
   * 查客户等级编码（如 VIP / MEMBER / ENTERPRISE）。
   *
   * @param customerCode 客户编码（订单 customerId 的字符串形态）
   * @return 等级编码；empty=客户未建档/未配置等级，或主数据服务不可达（调用方按标准价处理）
   */
  Optional<String> findCustomerLevelCode(String customerCode);

  /**
   * 查客户等级折扣率（如 0.88 表示 88 折）。
   *
   * @param levelCode 客户等级编码
   * @return 折扣率；empty=主数据未配置该等级，或主数据服务不可达（调用方用本地兜底折扣）
   */
  Optional<BigDecimal> findLevelDiscountRate(String levelCode);

  /** 商品主数据视图（领域层只认业务字段，不感知 masterdata 存储形态）。 */
  record ProductView(String code, String name, BigDecimal unitPrice) {}
}
