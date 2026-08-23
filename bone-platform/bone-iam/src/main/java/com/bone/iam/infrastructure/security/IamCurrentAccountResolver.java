package com.bone.iam.infrastructure.security;

import com.bone.core.security.auth.CurrentAccountResolver;
import com.bone.core.security.jwt.JwtPrincipal;

/** IAM 当前账号解析器。委托给框架层的 {@link CurrentAccountResolver}。 */
public final class IamCurrentAccountResolver {

  private IamCurrentAccountResolver() {}

  public static java.util.Optional<JwtPrincipal> currentPrincipal() {
    return CurrentAccountResolver.currentPrincipal();
  }

  public static java.util.Optional<Long> currentAccountId() {
    return CurrentAccountResolver.currentAccountId();
  }
}
