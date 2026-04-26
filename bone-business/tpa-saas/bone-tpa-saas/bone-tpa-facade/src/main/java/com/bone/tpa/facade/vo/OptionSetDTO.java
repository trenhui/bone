package com.bone.tpa.facade.vo;

import lombok.Data;

@Data
public class OptionSetDTO {
    //选项值code
    private String code ;
    //选项值的中文
    private String name ;
    /**
     * 字段是否开启other
     */
    private Boolean otherFlag = false;
    /**
     * other 的匹配值
     */
    private String otherMatchValue  ;


    /**
     * 扩展字段
     */
    private String extraProperty;

}
