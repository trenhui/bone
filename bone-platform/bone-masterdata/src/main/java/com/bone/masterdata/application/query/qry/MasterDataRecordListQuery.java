package com.bone.masterdata.application.query.qry;

import lombok.Data;

@Data
public class MasterDataRecordListQuery {
  private int page;
  private int size;
  private Long masterDataEntityId;
  private String status;
  private String keyword;

  /**
   * 仅返回当前生效记录（当前版本 + 生效窗口含此刻）。
   *
   * <p>真实场景：价格/客户主数据消费方只关心现在能用的那一条，过期记录留在历史页里， 不应混进"当前生效"结果集。
   */
  private Boolean onlyCurrent;
}
