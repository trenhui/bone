package com.bone.lowcode.infra.application.upload;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class BaseExcepDto {

    /**
     * 错误信息
     */
    @ExcelProperty("错误信息")
    private String errorMsg;
}
