package com.bone.iam.infrastructure.security;

import com.bone.iam.application.port.out.PasswordEncoderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Spring PasswordEncoder 适配器，桥接领域层 PasswordEncoderPort。 */
@Component
@RequiredArgsConstructor
public class PasswordEncoderPortAdapter implements PasswordEncoderPort {

  private final PasswordEncoder delegate;

  @Override
  public String encode(CharSequence rawPassword) {
    return delegate.encode(rawPassword);
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    return delegate.matches(rawPassword, encodedPassword);
  }
}
