package com.bone.iam.infrastructure.security;

import com.bone.core.security.auth.CurrentAccountResolver;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.iam.application.port.out.CurrentPrincipalPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** {@link CurrentPrincipalPort} 的框架实现，委托 {@link CurrentAccountResolver}。 */
@Component
public class CurrentPrincipalPortAdapter implements CurrentPrincipalPort {

  @Override
  public Optional<JwtPrincipal> currentPrincipal() {
    return CurrentAccountResolver.currentPrincipal();
  }
}
