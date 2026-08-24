package com.bone.metadata.catalog.application.command.cmd;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/**
 * 批量删除元数据实体命令。
 *
 * <p>部分成功语义：单个实体失败不影响其余，结果通过 {@code BatchDeleteMetaEntityHandler} 汇总返回。
 */
@Data
public class BatchDeleteMetaEntityCommand {
  @NotEmpty private List<Long> ids;
}
