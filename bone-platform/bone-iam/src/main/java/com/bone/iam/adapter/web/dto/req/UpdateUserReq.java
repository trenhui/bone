package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class UpdateUserReq {
    private String email;
    private Long[] roleIds;
}