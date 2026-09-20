package com.bone.iam.application.port.out;

/** 密码编码端口（Spring PasswordEncoder 的领域层抽象）。 */
public interface PasswordEncoderPort {

  String encode(CharSequence rawPassword);

  boolean matches(CharSequence rawPassword, String encodedPassword);
}
