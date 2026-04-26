package com.bone.tpa.hook.vo;

import com.bone.tpa.sdk.vo.HookPageconfigVO;
import lombok.Data;

import java.util.List;

@Data
public class HookConfigSaveRequest {

    private String bizIdentityCode ;

    private List<HookPageconfigVO> configList;
}
