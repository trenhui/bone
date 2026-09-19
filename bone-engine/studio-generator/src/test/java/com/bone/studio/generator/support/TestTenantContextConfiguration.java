package com.bone.studio.generator.support;

import com.bone.core.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 测试态租户上下文兜底（仅存在于 test classpath，随 {@code com.bone.studio.generator} 包被组件扫描）。
 *
 * <p>生产请求线程的 {@link TenantContext} 由 JWT Filter 写入；测试关闭鉴权后该来源缺失，租户表读 / 改 / 删 会触发 ADR-0029
 * 失败关闭（{@code MissingTenantContextException}）。此过滤器为无上下文的测试请求补上平台默认租户， 与 generator 资产 {@code
 * create(..., 0L, ...)} 的既有约定保持一致。
 *
 * <p>直接在测试线程调用 Handler / QueryHandler 的用例，需自行在 {@code @BeforeEach} 中调用 {@link #TEST_TENANT_ID} 对应的
 * {@code TenantContext.setTenantId(...)}。
 */
@Configuration
public class TestTenantContextConfiguration {

  /** 测试默认租户：与 generator 资产创建时写入的租户值一致。 */
  public static final long TEST_TENANT_ID = 0L;

  @Bean
  public OncePerRequestFilter testTenantContextFilter() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          HttpServletRequest request, HttpServletResponse response, FilterChain chain)
          throws ServletException, IOException {
        boolean injected = TenantContext.getTenantId() == null;
        if (injected) {
          TenantContext.setTenantId(TEST_TENANT_ID);
        }
        try {
          chain.doFilter(request, response);
        } finally {
          if (injected) {
            TenantContext.clear();
          }
        }
      }
    };
  }
}
