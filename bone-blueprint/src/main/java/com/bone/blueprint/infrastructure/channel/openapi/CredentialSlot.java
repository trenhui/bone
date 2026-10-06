package com.bone.blueprint.infrastructure.channel.openapi;

/**
 * 凭证槽位 —— 各平台「appKey / 密钥 / 令牌」在协议里的<b>参数名各不相同</b>，但取值来源只有这三个。
 *
 * <p><b>为什么用枚举而不是字符串</b>（2026-10-06 修正）：原实现是 {@code Map<String, String>}， 值写 {@code "appKey"}，客户端
 * {@code switch (entry.getValue()) case "appKey"} 派发。 这意味着<b>把 {@code "appKey"} 拼错成 {@code
 * "appkey"}</b> 时编译照过、运行照走， 只是落到 {@code default} 分支打一行 {@code log.warn}，然后<b>该渠道的凭证参数被静默不填</b>——
 * 渠道侧只会回一句「签名错误」，本地看不出是这里漏了。 改为枚举后，编译期即锁死取值域；新增槽位而漏改 {@code switch} 会编译失败而非静默。
 *
 * <p>槽位与参数名的映射（谁叫什么）由各 {@link ChannelApiSpec#credentialKeys()} 声明。
 */
public enum CredentialSlot {

  /** 应用标识：淘宝 {@code app_key} / 拼多多 {@code client_id}。 */
  APP_KEY,

  /** 应用密钥：淘宝 {@code app_secret} / 拼多多 {@code client_secret}。 */
  APP_SECRET,

  /** 授权令牌：淘宝与拼多多 {@code access_token} / 京东 {@code token}。 */
  ACCESS_TOKEN;

  /**
   * 从凭证取值。
   *
   * <p>用 {@code switch} 覆盖全部枚举值而非 {@code default}：将来新增槽位时，这里编译失败， 强制作者去 {@code
   * ChannelOpenApiClient} 补上取值分支，而不是让它掉进「静默跳过」。
   */
  public String valueOf(ChannelCredentials credentials) {
    return switch (this) {
      case APP_KEY -> credentials.appKey();
      case APP_SECRET -> credentials.appSecret();
      case ACCESS_TOKEN -> credentials.accessToken();
    };
  }
}
