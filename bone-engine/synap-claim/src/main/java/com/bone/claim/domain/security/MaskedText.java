package com.bone.claim.domain.security;

import java.util.Map;

/**
 * 遮蔽结果（持有可逆映射，调用方在请求作用域内持有，禁止落盘/入 trace）。
 *
 * @param masked 已遮蔽文本（可安全送云端 LLM 通道）
 * @param tokenMap 占位符 → 原始值（仅本请求内存）
 */
public record MaskedText(String masked, Map<String, String> tokenMap) {

  /** 还原为原始文本（本地 vLLM 通道或结果回填时使用）。 */
  public String unmask() {
    String result = masked;
    for (Map.Entry<String, String> e : tokenMap.entrySet()) {
      result = result.replace(e.getKey(), e.getValue());
    }
    return result;
  }
}
