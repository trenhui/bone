package com.bone.engine.extension.studio.domain.gateway;

import org.springframework.lang.Nullable;

/**
 * 数据面上报令牌的校验能力（出站端口，domain 层不感知配置来源与比对实现）。
 *
 * <p>存在意义：把"上报者是不是自己人"这个判定从 adapter 层收进可替换的端口， 避免令牌比对逻辑散进SecurityConfig —— 后者是配置类，混进业务判定后无法单测。
 */
public interface ReporterTokenValidator {

  /**
   * 上报请求携带的令牌是否可接受。
   *
   * <p>实现须是**失败关闭**的：无法确认身份（密钥未配置、请求未携带令牌、密钥不匹配）时返回 false， 调用方不得继续落库。
   *
   * @param presented 请求头 {@code X-Reporter-Token} 的原始值，可为 null
   * @return true 表示放行进业务方法；false 表示拒绝（调用方须失败关闭，不得继续落库）
   */
  boolean isAcceptable(@Nullable String presented);

  /**
   * 服务端是否已配置机器身份密钥。
   *
   * <p>与"令牌是否正确"是两件事，必须能被调用方区分：
   *
   * <ul>
   *   <li>未配置 ⇒ 服务端部署问题，客户端补令牌也没用 ⇒ 应答 {@code 503 Service Unavailable}
   *   <li>已配置但不匹配 ⇒ 客户端身份问题 ⇒ 应答 {@code 401 Unauthorized}
   * </ul>
   *
   * <p>合成一个布尔值会让上报方无法区分"我该去改部署配置"还是"我该去查自己的密钥"， 排障成本很高。
   *
   * @return true 表示已配置非空密钥
   */
  boolean isConfigured();
}
