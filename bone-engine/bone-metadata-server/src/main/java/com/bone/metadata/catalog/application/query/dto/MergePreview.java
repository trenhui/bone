package com.bone.metadata.catalog.application.query.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 模板跟随三路归并预览（服务端算，前端只确认）。
 *
 * <p>策略：租户未改过的段（与 {@code base.old} 逐段一致）自动取 {@code base.new}；改过的保留实例值 +
 * WARNING。无历史快照时降级为两路（base.new vs instance），冲突面全量标出。
 */
@Data
@Builder
public class MergePreview {

  /** 自动合入的段路径（如 {@code section:sec_base}）。 */
  private List<String> autoMerged;

  private List<MergeConflict> conflicts;

  /** {@code true}=两路降级（模板无历史快照），前端横幅明示"请逐项确认"。 */
  private boolean degradedTwoWay;

  @Data
  @Builder
  public static class MergeConflict {

    /** 段路径（如 {@code linkage:LINK-FAST-01}）。 */
    private String path;

    /** base.new 的段 JSON（模板新版）。 */
    private String baseNew;

    /** instance 的段 JSON（租户现值）。 */
    private String instance;
  }
}
