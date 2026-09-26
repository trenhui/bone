package com.bone.system.adapter.web.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/** 枚举与字典的漂移报告（见 DictEnumDiffDto 的处置说明）。 */
@Data
@Builder
public class DictEnumDiffResp {
  private String typeCode;
  private String enumClass;

  /** 枚举有、字典无：同步即可。 */
  @Builder.Default private List<String> missingInDict = List.of();

  /** 字典有、枚举无：代码已删而字典未清，属危险漂移。 */
  @Builder.Default private List<String> missingInEnum = List.of();

  /** value 与 ordinal 不一致的项编码。 */
  @Builder.Default private List<String> valueDrift = List.of();

  private boolean consistent;
}
