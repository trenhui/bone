package com.bone.lowcode.infra.application.upload.dto;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.bone.lowcode.infra.application.upload.BaseExcepDto;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class OptionSetImportDto extends BaseExcepDto {
    /**
     * 元素标识
     */
    @ExcelProperty("code")
    private String code;
    @ExcelProperty("中文")
    private String name;
    /**
     * 选项值启用状态,0:不启用,1:启用
     */
    @ExcelProperty("状态")
    private String status;



}
