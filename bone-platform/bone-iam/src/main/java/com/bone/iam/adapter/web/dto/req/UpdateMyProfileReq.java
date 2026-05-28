package com.bone.iam.adapter.web.dto.req;

import lombok.Data;

@Data
public class UpdateMyProfileReq {
    private String realName;
    private String phone;
    private String avatarUrl;
}
