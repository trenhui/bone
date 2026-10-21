package com.bone.aiintegration.domain.gateway;

/**
 * 入站回调验签端口（保司 / 直付 / 医院等外部系统的异步回调真伪校验）。
 *
 * <p><b>真实欺诈向量</b>：TPA 接收外部系统的核赔结果 / 支付结果回调，伪造回调可把未决赔案标成「已赔付 / 已通过」—— 直接造成资金损失。 回调必须验签后才可采信，与出站
 * {@link com.bone.ainotification.infrastructure.notification.WebhookSigner} （本平台签发出站回调）形成「出入对称」。
 *
 * <p>消费方在回调入口（控制器 / 消息消费）先验签、后处理；验签失败抛 {@link SecurityException}（认证失败），由入口映射为 401 / 拒绝。 默认实现 {@code
 * HmacCallbackVerifier}（HMAC-SHA256，常量时间比较）；接更强方案（非对称签名 / 云端验签）以 {@code @Bean} 覆盖。
 */
public interface CallbackVerifier {

  /**
   * 校验回调真实性；失败抛 {@link SecurityException}（fail-closed：验不过就不采信）。
   *
   * @param payload 回调请求体原文（须与签名时的字节一致）
   * @param signature 回调携带的签名（支持 {@code sha256=<hex>} 或纯 hex，大小写不敏感）
   */
  void verify(String payload, String signature);
}
