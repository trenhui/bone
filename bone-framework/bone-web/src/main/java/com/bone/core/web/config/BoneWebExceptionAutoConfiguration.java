package com.bone.core.web.config;

import com.bone.core.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * bone-web 的自动配置：注册框架统一异常处理器 {@link GlobalExceptionHandler}。
 *
 * <p><b>为什么需要这个类（修的是什么缺陷）</b>：{@code GlobalExceptionHandler} 所在包 {@code com.bone.core.exception}
 * 此前<strong>没有任何注册入口</strong>——bone-web 没有 auto-configuration，平台各模块也没有一个 扫描该包（全仓 grep
 * 可验证）。于是它在每个运行中的应用里都是<strong>死代码</strong>：业务异常（如 {@code BizException(code=404)}）不经翻译直接落到 servlet
 * 容器，客户端拿到的是 <strong>HTTP 500</strong> 而不是 404/409 + 错误码信封——实机验证复现于 bone-blueprint（`GET
 * /api/v1/orders/{不存在}` 返回 500，修复后 404）。
 *
 * <p><b>为什么用「按 Bean 名跳过」而不是「按类型跳过」</b>：平台里 {@code system} / {@code masterdata} / {@code
 * metadata-server} 三个模块各自实现了<strong>同名</strong>类 {@code GlobalExceptionHandler}（都是
 * {@code @RestControllerAdvice}，均为包内自定义实现，含模块特有的告警/ORM 异常分支）。它们的默认 Bean 名同样是 {@code
 * globalExceptionHandler}——既不能「按类型」判断（类型不同，判断不到），也不应该注册第二个 advice 造成「同一个异常由哪个 advice 处理」不确定。因此这里以
 * <strong>Bean 名 {@code globalExceptionHandler} 是否存在</strong>为条件：模块自带处理器时
 * 本自动配置整体让路（保持既有行为），没有自带的模块（iam / integration / file / blueprint / gateway / studio 等）则由 框架补上。
 *
 * <p><b>注册方式</b>：见 {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}； 模块无需任何
 * {@code @ComponentScan} 或 {@code @Import}。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnMissingBean(name = "globalExceptionHandler")
public class BoneWebExceptionAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public GlobalExceptionHandler globalExceptionHandler() {
    return new GlobalExceptionHandler();
  }
}
