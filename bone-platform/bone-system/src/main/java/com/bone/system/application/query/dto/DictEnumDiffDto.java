package com.bone.system.application.query.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 枚举与字典的漂移报告。
 *
 * <p>三组的处置方式不同，所以分开返回而不是合成一个「不一致列表」：
 *
 * <ul>
 *   <li>{@code missingInDict}——枚举有、字典无：同步即可，属正常新增；
 *   <li>{@code missingInEnum}——字典有、枚举无：<b>危险</b>，代码里已无对应分支，UI 却能选到；
 *   <li>{@code valueDrift}——{@code value} 与 {@code ordinal()} 不一致：序列化口径漂移。
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictEnumDiffDto {
  private String typeCode;
  private String enumClass;

  /** 枚举有、字典无。 */
  @Builder.Default private List<String> missingInDict = List.of();

  /** 字典有、枚举无（代码已删而字典未清）。 */
  @Builder.Default private List<String> missingInEnum = List.of();

  /** value 与 ordinal 不一致的项编码。 */
  @Builder.Default private List<String> valueDrift = List.of();

  public boolean isConsistent() {
    return missingInDict.isEmpty() && missingInEnum.isEmpty() && valueDrift.isEmpty();
  }
}
