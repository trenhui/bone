package com.bone.tpa.intelligent.adjustment.model;

import lombok.Data;

/**
 * 用于绑定的短责任对象
 */
@Data
public class LiabilityToBind {

    /**
     * 该责任的uuid
     */
    private String uuid;

    /**
     * 该责任的名称
     */
    private String name;

    /**
     * 是否有效
     * 如果查出来是无效，前端需要展示成红色
     *
     * ACTIVE
     * DELETED
     * INACTIVE
     *
     */
    private String active = "ACTIVE";
}
