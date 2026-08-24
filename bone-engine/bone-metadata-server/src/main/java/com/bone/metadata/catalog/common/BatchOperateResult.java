package com.bone.metadata.catalog.common;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 批量操作汇总结果。
 *
 * <p>与前端 {@code metadataApi.batchPublish/batchDelete} 约定的 {@code {successCount, failCount, errors}}
 * 结构保持一致。
 */
@Data
@AllArgsConstructor
public class BatchOperateResult {
  private int successCount;
  private int failCount;
  private List<String> errors;
}
