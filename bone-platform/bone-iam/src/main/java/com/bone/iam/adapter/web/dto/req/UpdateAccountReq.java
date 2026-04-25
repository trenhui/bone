package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class UpdateAccountReq {
    private String email;
    private String phone;
    private String realName;
    private Integer status;
    private Long[] roleIds;
}
