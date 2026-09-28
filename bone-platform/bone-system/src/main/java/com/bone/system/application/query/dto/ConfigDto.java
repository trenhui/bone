package com.bone.system.application.query.dto;

import com.bone.system.domain.model.config.SystemConfig;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配置的应用投影（adapter 可见的最终形态，E-10.1 转换边界）。
 *
 * <p><b>不是聚合的替身</b>：聚合是可变的写模型，投影是不可变的读模型；不允许反向用它改业务状态（CORE-05）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigDto {
  private Long id;
  private String configKey;
  private String configValue;
  private String description;
  private String configType;
  private boolean encrypted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 加密值的统一脱敏形态；快照导入也正是靠识别它来跳过加密项。 */
  public static final String MASKED_VALUE = "******";

  /**
   * 聚合 → 应用投影。
   *
   * <p><b>加密项为什么在这一层脱敏而不是在 Controller</b>：配置值可能承载密钥，一旦把聚合交给 adapter
   * 就有被原样序列化的风险。脱敏放在投影出口，换一个入站协议（RPC / 定时任务）也漏不掉这一步。
   */
  public static ConfigDto from(SystemConfig config) {
    return ConfigDto.builder()
        .id(config.getId())
        .configKey(config.getConfigKey().value())
        .configValue(config.isEncrypted() ? MASKED_VALUE : config.getConfigValue().value())
        .description(config.getDescription())
        .configType(config.getConfigType().name())
        .encrypted(config.isEncrypted())
        .createdAt(config.getCreatedAt())
        .updatedAt(config.getUpdatedAt())
        .build();
  }
}
