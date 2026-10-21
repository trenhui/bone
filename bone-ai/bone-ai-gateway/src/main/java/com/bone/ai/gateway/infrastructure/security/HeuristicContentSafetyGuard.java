package com.bone.ai.gateway.infrastructure.security;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.ai.gateway.domain.gateway.ContentSafetyGuard;
import com.bone.core.exception.BizException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 输出内容安全护栏（默认启发式实现）：轻量关键词命中检测模型产出中的不可接受内容，零依赖。
 *
 * <p><b>误伤与默认关闭</b>：关键词规则有误伤风险，装配层默认不启用（{@code
 * bone.ai.gateway.content-safety-guard-enabled=false}）； 需要语义级 / 云端内容安全时以 {@code @Bean}
 * 覆盖（属外部依赖，AGENTS L3）。默认特征聚焦「提示注入被绕过、模型遵从恶意指令」的越狱成功标志， 措辞偏具体以降低误伤。
 *
 * <p>归一化：小写 + 压缩连续空白后子串匹配（同 {@link HeuristicInjectionGuard}）。
 */
public class HeuristicContentSafetyGuard implements ContentSafetyGuard {

  /** 默认不可接受输出特征（越狱成功标志；中英文，措辞具体以降误伤）。 */
  public static final List<String> DEFAULT_PATTERNS =
      List.of(
          // 英文：模型遵从注入 / 宣称解除限制
          "i will ignore",
          "i'll ignore my",
          "i have ignored my",
          "i have no restrictions",
          "as an ai with no limits",
          "jailbreak successful",
          "developer mode enabled",
          // 中文
          "我将忽略我的",
          "我会忽略我的",
          "我已忽略我的",
          "我已解除所有限制",
          "我没有任何限制",
          "越狱成功",
          "开发者模式已启用");

  private final Set<String> patterns;

  public HeuristicContentSafetyGuard() {
    this(DEFAULT_PATTERNS);
  }

  public HeuristicContentSafetyGuard(Collection<String> customPatterns) {
    if (customPatterns == null || customPatterns.isEmpty()) {
      throw new IllegalArgumentException("内容安全特征集合不可为空");
    }
    this.patterns =
        customPatterns.stream()
            .filter(p -> p != null && !p.isBlank())
            .map(HeuristicContentSafetyGuard::normalize)
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public void check(String modelOutput, String traceId) {
    if (modelOutput == null || modelOutput.isBlank()) {
      return;
    }
    String norm = normalize(modelOutput);
    for (String pattern : patterns) {
      if (norm.contains(pattern)) {
        throw new BizException(
            400,
            "模型产出含不可接受内容（命中特征: " + pattern + "）: traceId=" + traceId,
            AiGatewayErrorCodes.CONTENT_UNSAFE);
      }
    }
  }

  private static String normalize(String s) {
    return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
  }
}
