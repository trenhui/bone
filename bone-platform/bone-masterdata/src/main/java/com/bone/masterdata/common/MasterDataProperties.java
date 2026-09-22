package com.bone.masterdata.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 主数据模块的容量上限（{@code masterdata.*}）。
 *
 * <p>放在 {@code common} 而非 {@code infrastructure}：应用服务要用它做入口校验，而 P0-1 门禁禁止 application 依赖
 * infrastructure。
 *
 * <p>这些值必须在用例入口真正校验，否则就只是装饰性配置——过去正是如此。
 */
@Component
@ConfigurationProperties(prefix = "masterdata")
public class MasterDataProperties {

  /** 单个实体允许的字段数上限。 */
  private int entityMaxFields = 100;

  /** 单条主数据记录的 data 字符数上限。 */
  private int recordMaxSize = 1048576;

  /** 单次导出允许的最大记录数。 */
  private int exportMaxRecords = 10000;

  public int getEntityMaxFields() {
    return entityMaxFields;
  }

  public void setEntityMaxFields(int entityMaxFields) {
    this.entityMaxFields = entityMaxFields;
  }

  public int getRecordMaxSize() {
    return recordMaxSize;
  }

  public void setRecordMaxSize(int recordMaxSize) {
    this.recordMaxSize = recordMaxSize;
  }

  public int getExportMaxRecords() {
    return exportMaxRecords;
  }

  public void setExportMaxRecords(int exportMaxRecords) {
    this.exportMaxRecords = exportMaxRecords;
  }
}
