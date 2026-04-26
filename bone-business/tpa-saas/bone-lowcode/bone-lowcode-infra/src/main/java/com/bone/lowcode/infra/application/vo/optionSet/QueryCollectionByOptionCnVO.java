package com.bone.lowcode.infra.application.vo.optionSet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryCollectionByOptionCnVO {

    //选项值code
    private String code;

    //选项值的name
    private String name;

    /**
     * 字段是否开启other
     */
    private boolean otherFlag = false;
    /**
     * other 的匹配值
     */
    private String otherMatchValue  ;


    private String extraProperty;
}
