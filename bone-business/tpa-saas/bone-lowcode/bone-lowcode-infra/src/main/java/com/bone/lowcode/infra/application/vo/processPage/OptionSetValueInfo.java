package com.bone.lowcode.infra.application.vo.processPage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptionSetValueInfo {

    /**
     * 选项值ID
     */
    private String id;

    /**
     * 选项值标识
     */
    private String valueCode;

    /**
     * 选项值名称
     */
    private String valueName;
}
