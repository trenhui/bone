package com.bone.system.application.command;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配置快照导入结果（MVP-09）。
 *
 * <p>逐条统计而不只返回成功/失败：配置导入是「部分成功」语义——某条键非法或为加密项不应让整批回滚， 调用方需要知道到底写进去了几条、跳过了哪几条。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigSnapshotImportResult {
  private int created;
  private int updated;
  private int skipped;

  /** 跳过原因（逐条），供页面提示用户手工补录。 */
  @Builder.Default private List<String> reasons = new ArrayList<>();
}
