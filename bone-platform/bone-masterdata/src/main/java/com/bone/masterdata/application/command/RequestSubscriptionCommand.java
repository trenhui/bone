package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 订阅申请命令（G10）。subscribeMode：READ / EVENT，缺省 READ。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestSubscriptionCommand {

  @NotNull(message = "masterDataEntityId: 主数据实体ID不能为空")
  private Long masterDataEntityId;

  @NotNull(message = "appId: 消费应用ID不能为空")
  private Long appId;

  private String subscribeMode;
}
