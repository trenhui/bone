package com.bone.lowcode.infra.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectDatasource {

    /**
     * 数据源类型,1:选项集
     */
    private Byte type;

    /**
     * 数据源code
     */
    private String code;
}
