package com.bone.masterdata.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 值域更新入参。
 *
 * <p>id 不加 {@code @NotNull}：它由 Controller 的路径变量回填（{@code cmd.setId(pathId)}），而 Spring 的
 * {@code @Valid} 在进入方法体之前就已执行，若在此约束 id，校验时字段仍为 null 会直接判 400，导致更新功能完全不可用。该字段不参与客户端提交。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReferenceSetCommand {

  private Long id;

  private String setName;
  private String externalStandard;
  private String description;
}
