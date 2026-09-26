package com.bone.masterdata.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReferenceValueCommand {

  /** 由控制器从路径变量回填，不参与请求体校验（@Valid 先于 @PathVariable 绑定执行）。 */
  private Long setId;

  @NotBlank(message = "valueCode: 值编码不能为空")
  private String valueCode;

  @NotBlank(message = "valueName: 值名称不能为空")
  private String valueName;

  private String externalCode;
  private Integer sortOrder;
}
