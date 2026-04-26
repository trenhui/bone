package com.bone.tpa.api;

import lombok.Data;

@Data
public class ApiResult<T> {
    private static final Integer SUCCESS = 0;
    private T data;
    private int code = 0;
    private String message;

    public boolean isSuccess(){
        return SUCCESS == code;
    }

}
