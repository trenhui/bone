package com.bone.lowcode.infra.application.vo.bizIdentity;

import lombok.Data;

@Data
public class ZfObjectPageVO {

    private Integer bizType;

    /**
     * 对象的code，联动的时候需要当做parentCode 传入
     */
    private String code;

    /**
     * 对象的名称
     */
    private String name;
}
