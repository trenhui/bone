package com.bone.blueprint.infrastructure.channel.openapi;

import java.util.Map;

/**
 * 渠道开放平台 HTTP 传输抽象。
 *
 * <p><b>为什么为「发一个表单请求」也开一层接口</b>：协议规格（签名 / 网关 / 响应解析）能被单测锁住，靠的正是这一层可被替换—— 测试把 {@code Executor}
 * 换成桩，就能断言「淘宝拉单的签名串、参数顺序、Header 令牌」这些<strong>不出网也必须正确</strong>的逻辑。 若客户端直接 new {@code
 * java.net.http.HttpClient}，这些逻辑只能靠人工对着渠道后台肉眼核对。
 */
public interface ChannelHttpExecutor {

  /**
   * 表单编码 POST。
   *
   * @param url 渠道网关
   * @param headers 额外请求头（令牌 Header 走这里）
   * @param form 表单参数（含签名）
   */
  ChannelHttpResponse post(String url, Map<String, String> headers, Map<String, String> form);
}
