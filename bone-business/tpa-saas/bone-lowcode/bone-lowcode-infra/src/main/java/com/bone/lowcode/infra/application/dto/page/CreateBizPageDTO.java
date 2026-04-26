package com.bone.lowcode.infra.application.dto.page;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateBizPageDTO {

    private Long bizIdentityId;

    @NotBlank(message = "业务主体code不能为空")
    private String bizIdentityCode;
}
