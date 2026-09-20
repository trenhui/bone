package com.bone.iam.application.port.out;

import com.bone.core.security.jwt.JwtPrincipal;
import java.util.Optional;

/**
 * 当前登录主体端口（从认证上下文中解析）。
 *
 * <p>按 E-10.2 放置为技术能力端口：安全上下文解析是框架能力，不是本上下文的领域概念，故不属 {@code domain/gateway}；由 {@code
 * infrastructure/security} 实现，使 {@code adapter} 层（如 {@code MeController}） 不必反向依赖 {@code
 * infrastructure}（E-10.1）。
 */
public interface CurrentPrincipalPort {

  /** 当前请求的已认证主体；未认证返回 {@code Optional.empty()}。 */
  Optional<JwtPrincipal> currentPrincipal();
}
