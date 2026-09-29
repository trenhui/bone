package com.bone.studio.generator.infrastructure.gateway;

import com.bone.studio.generator.domain.gateway.BuiltInTemplateGateway;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 内置模板正文读取器：读 classpath 下 {@code templates/{code}.ftl}，与 {@code TemplateRenderer} 同一路径约定。 */
@Slf4j
@Component
public class BuiltInTemplateGatewayAdapter implements BuiltInTemplateGateway {

  @Override
  public Optional<String> contentOf(String code) {
    if (code == null || code.isEmpty()) {
      return Optional.empty();
    }
    ClassPathResource resource = new ClassPathResource("templates/" + code + ".ftl");
    if (!resource.exists()) {
      return Optional.empty();
    }
    try (InputStream in = resource.getInputStream()) {
      return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      // 读不到就当没有内置模板，让上层回落到「模板不存在」而非把 IO 异常抛给租户
      log.warn("内置模板读取失败，按不存在处理: code={}", code, e);
      return Optional.empty();
    }
  }
}
