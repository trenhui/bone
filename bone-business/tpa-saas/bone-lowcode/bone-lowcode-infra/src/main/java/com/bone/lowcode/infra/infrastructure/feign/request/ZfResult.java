package com.bone.lowcode.infra.infrastructure.feign.request;

import lombok.Data;

@Data
public class ZfResult<T> {
    /**
     * 0  成功
     */
    private Integer code ;

    private T data;

    private String message;


    private Integer totalPages;

    public boolean isSuccess(){
        return code == 0;
    }
}
