package com.bone.claim.infrastructure.config;

import com.bone.core.web.interceptor.TenantInterceptor;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * synap-claim Web MVC 配置（与 bone-blueprint 同范式）。
 *
 * <p>注册 {@link TenantInterceptor}：从（已被 {@code AbstractJwtAuthenticationFilter} 归一化为 token 已签名 claim
 * 的）{@code X-Tenant-Id} 头建立 {@code com.bone.core.tenant.context.TenantContext}——理赔领域 {@code
 * TenantPortAdapter#currentTenantId()} 与 bone-metadata-sdk 的租户数据源路由都消费该上下文。 拦截器 fail-closed：头缺失即抛
 * {@code MissingTenantContextException}（ADR-0029），不回落到平台租户 0。
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new TenantInterceptor()).addPathPatterns("/**");
  }

  /**
   * C1（P0）BIGINT 主键防 JS 截断：全局把 {@code Long} 序列化为字符串。
   *
   * <p><b>为什么必须做</b>：主键由 {@code DistributedIdGenerator}（雪花算法）生成 64 位值，远超 JS 安全整数 {@code 2^53}；若以
   * {@code number} 下发，{@code JSON.parse} 在客户端就近舍入，ID 静默损坏 ⇒ 串案 / 404 / 错案核赔。 故所有 {@code Long}
   * 一律以字符串形式下发，前端 {@code claimId: string} 类型方向才真正闭环。
   *
   * <p><b>为什么覆盖 MVC 消息转换器而非依赖全局 Module bean 自动注册</b>：本模块编译期不可见 {@code spring-boot-autoconfigure}
   * 的定制器类，故直接在 {@link #extendMessageConverters} 里把序列化模块挂到 Spring MVC 实际使用的 {@link
   * MappingJackson2HttpMessageConverter} 的 {@code ObjectMapper} 上，效果确定、零外部依赖。
   *
   * <p><b>为什么不破坏金额 / 计数</b>：金额统一为 {@code BigDecimal}（不受 {@code Long} 序列化器影响）， 分页 {@code total}
   * 等计数前端已用 {@code string | number} 容错；写路径回传的 string ID 由 Jackson 自动 coerce 回 {@code Long}。
   */
  @Override
  public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    Module module = longIdAsStringModule();
    for (HttpMessageConverter<?> converter : converters) {
      if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
        jackson.getObjectMapper().registerModule(module);
      }
    }
  }

  /** Long → 字符串序列化模块（雪花主键 &gt; 2^53，须以字符串下发）。 */
  private Module longIdAsStringModule() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(Long.class, ToStringSerializer.instance);
    module.addSerializer(Long.TYPE, ToStringSerializer.instance);
    return module;
  }
}
