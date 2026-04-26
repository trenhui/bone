package com.bone.lowcode.infra.application.vo.optionSet;

import com.bone.lowcode.infra.application.vo.PageResult;
import lombok.Data;

@Data
public class OptionValuePageVO {

    /**
     * 选项值额外属性名,数组格式
     */
    private String extraPropertyKey;

    /**
     * 选项值分页结果
     */
    private PageResult<OptionValueVO> optionValueInfo;
}
