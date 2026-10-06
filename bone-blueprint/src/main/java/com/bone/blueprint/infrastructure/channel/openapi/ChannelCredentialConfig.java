package com.bone.blueprint.infrastructure.channel.openapi;

import lombok.Getter;
import lombok.Setter;

/**
 * 单个渠道开放平台的凭证配置。
 *
 * <p><b>为什么凭证走配置而不是落库</b>：{@code appSecret / clientSecret} 属于密钥材料。库里存明文密钥意味着
 * 一次库权限泄露＝渠道商户权限泄露，且无法按渠道轮换；配置中心 / 环境变量至少可以把密钥的读取权限与业务数据权限分开。
 * 本类只做<strong>本地联调与容器化部署</strong>的载体，真正生产应当接入密钥管理后由 {@code ChannelCredentialProvider} 增补一个来源，
 * 届时本类退化为「缺失时的兜底」而不是唯一真源。
 *
 * <p>全部字段<strong>默认空</strong>且<strong>不得有默认值语义</strong>：拿不到凭证必须失败，而不是拿空串去调渠道
 * （空串会被渠道判为签名错误，看起来像「渠道挂了」，实际是「凭证没配」）。
 */
/**
 * 注意：本类不标注 {@code @ConfigurationProperties}——它作为 {@code
 * bone.blueprint.channel.openapi.credentials.<渠道码>} 映射的**元素类型**由外层 binder 消费， 自带前缀会把它绑成第二份独立配置。
 */
@Getter
@Setter
public class ChannelCredentialConfig {

  /** 渠道 appKey / client_id。 */
  private String appKey = "";

  /** 渠道 appSecret / client_secret（密钥材料，仅内存与日志脱敏后使用）。 */
  private String appSecret = "";

  /** 授权令牌（长期令牌 / 刷新后写入的 access_token）。 */
  private String accessToken = "";

  /** 令牌过期时间（ISO-8601）；留空表示不过期。 */
  private String expiresAt = "";
}
