package com.bone.blueprint.infrastructure.channel.openapi;

import java.util.Map;

/**
 * 渠道开放平台协议规格 —— 「这个平台的网关在哪、怎么签、令牌放哪、响应长什么样」的唯一真源。
 *
 * <p><b>为什么协议知识要单独成层，而不是埋在 12 个渠道扩展实现里</b>：扩展实现是<strong>渠道业务语义</strong>的归属（同一个接口，淘宝叫 {@code
 * outer_item_id}、京东叫 {@code skuId}，只有拉单实现知道该读哪个字段）；
 * 而协议知识（签名、公共参数、网关、响应封装）四个渠道<strong>两两相似但没人完全相同</strong>。
 * 把二者混在一起，接第五个渠道时要同时读改「协议片段」与「业务映射」，漏改一处就出现「联调一切正常、上线后渠道回 50008」。
 *
 * <p><b>实现只声明参数，流程由客户端统一执行</b>：{@link #commonParams} 给出平台公共参数、 {@link #sign} 只负责产出签名串，
 * 而组装、超时、重试、日志、令牌刷新全部在 {@link ChannelOpenApiClient}——规格里不写流程，只写「这个平台是什么样」。
 */
public interface ChannelApiSpec {

  /** 渠道码（TAOBAO / JD / DOUYIN / PDD）。 */
  String channelCode();

  /** 平台网关地址。 */
  String gateway();

  /** 签名算法。 */
  ChannelSignatureAlgorithm signature();

  /** 令牌作为 URL 参数时的参数名（{@code tokenAsHeader} 为 false 时生效）。 */
  String tokenParamName();

  /** 令牌是否放 Header。 */
  boolean tokenAsHeader();

  /** 令牌 Header 名（{@code tokenAsHeader} 为 true 时生效）。 */
  String tokenHeaderName();

  /**
   * 渠道凭证 → 请求参数的映射（参数名 → 凭证字段名）。
   *
   * <p>拼多多把 appKey 叫 {@code client_id}、京东把令牌叫 {@code token}，字段名差异若散落在客户端就变成四个 if； 收在规格里后， 客户端只认
   * {@code appKey / appSecret / accessToken} 三个凭证槽位。
   */
  Map<String, String> credentialKeys();

  /**
   * 平台公共参数（不含业务参数、令牌与签名）。
   *
   * @param apiMethod 平台接口名，形如 {@code taobao.trade.orders.get}
   * @param timestampSeconds 秒级时间戳（各平台时间格式不同，由实现决定格式）
   */
  Map<String, String> commonParams(String apiMethod, long timestampSeconds);

  /** 产出签名值（输入为「公共参数 + 令牌 + 业务参数」，不含 sign 本身）。 */
  String sign(Map<String, String> signedParams, String secret);

  /** 解析平台响应为归一化结果。 */
  ChannelApiResult parse(String rawBody);
}
