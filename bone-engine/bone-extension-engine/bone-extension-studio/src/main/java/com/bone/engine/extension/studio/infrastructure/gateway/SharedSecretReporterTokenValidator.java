package com.bone.engine.extension.studio.infrastructure.gateway;

import com.bone.engine.extension.studio.config.ExtensionStudioProperties;
import com.bone.engine.extension.studio.domain.gateway.ReporterTokenValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 共享密钥校验实现（常量时间比对）。
 *
 * <p><b>失败关闭</b>：令牌未配置时是否放行由 {@code security.reporter-auth-required} 决定，<b>默认要求配置</b>
 * ——安全配置的默认必须是"拒绝"。若默认放行，"忘记配置"就是一次静默降级：上报端点退化为匿名可写， 只有一条 WARN 日志，足够被忽略；而漏配的后果是执行日志被无声污染。
 *
 * <p>实现里还有一处容易被忽略的细节：请求未携带令牌与令牌不匹配都返回 false，但**原因不同** （前者可能是 SDK 未配置
 * token，后者是密钥错误或伪造尝试），调用方据此选状态码，见 {@link ReporterTokenValidator#isConfigured()}。
 */
@Component
public class SharedSecretReporterTokenValidator implements ReporterTokenValidator {

  private static final Logger log =
      LoggerFactory.getLogger(SharedSecretReporterTokenValidator.class);

  private final byte[] expected;

  /** 未配置密钥时是否拒绝一切上报（默认 true = 失败关闭）。 */
  private final boolean requiredWhenMissing;

  public SharedSecretReporterTokenValidator(ExtensionStudioProperties properties) {
    String token = properties.getSecurity().getReporterToken();
    this.expected =
        StringUtils.hasText(token) ? token.trim().getBytes(StandardCharsets.UTF_8) : null;
    this.requiredWhenMissing = properties.getSecurity().isReporterAuthRequired();
    if (expected == null) {
      if (requiredWhenMissing) {
        // ERROR 而非 WARN：这不是"需要留意"，而是"上报功能当前不可用"。
        // 用 WARN 会让它混在常规噪声里被忽略，业务方不会发现自己的日志一直没上报。
        log.error(
            "bone.extension.studio.security.reporter-token 未配置 且 reporter-auth-required=true"
                + " —— 数据面执行日志上报端点（/api/v1/extension/execution-logs:ingest）"
                + "将拒绝一切上报（HTTP 503）。这是**失败关闭**行为，不是故障："
                + "请注入 BONE_EXTENSION_REPORTER_TOKEN；本地联调可显式置 "
                + "BONE_EXTENSION_REPORTER_AUTH_REQUIRED=false 临时放行");
      } else {
        log.warn(
            "bone.extension.studio.security.reporter-token 未配置 且 reporter-auth-required=false"
                + " —— 数据面上报端点处于匿名可写状态，仅适用于本地联调，生产必须配置该密钥");
      }
    }
  }

  @Override
  public boolean isConfigured() {
    return expected != null;
  }

  @Override
  public boolean isAcceptable(@Nullable String presented) {
    if (expected == null) {
      // 失败关闭的开关在这里生效：未配置密钥时，是否放行由配置决定而非隐式 true。
      return !requiredWhenMissing;
    }
    if (!StringUtils.hasText(presented)) {
      return false;
    }
    return MessageDigest.isEqual(expected, presented.trim().getBytes(StandardCharsets.UTF_8));
  }
}
