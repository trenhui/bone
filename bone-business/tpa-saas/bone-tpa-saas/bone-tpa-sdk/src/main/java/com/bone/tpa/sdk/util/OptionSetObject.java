package com.bone.tpa.sdk.util;

import lombok.Data;

@Data
public class OptionSetObject {
    /**
     * 所属选项集code
     */
    private String code ;
    /**
     * 所属选项集中文
     */
    private String name ;

    /**
     * 是否是其他选项
     */
    private Boolean otherFlag = false ;
    /**
     * 其他的文案
     */
    private String otherContent ;
}
