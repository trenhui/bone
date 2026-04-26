package com.bone.tpa.facade.request;

import lombok.Data;

import java.util.List;

@Data
public class CollectionBindQueryRequest {
    /**
     *  因为yapi实现不了对数组的mock，所以这里用字符串
     */
    private String modelCodeMockTag ;
    //identitycode
    private String bizIdentityCode;

    /**
     * 所属模块list
     * 因为比如出险信息和赔案，都是属于赔案表，因此搞这个
     */
    private List<String> modelCodeList;

}
