package com.bone.tpa.hook.vo;

import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.claim.sync.Context;
import lombok.Data;

import java.util.Map;

@Data
public class FromTpaPreHookParam {
    private  ClaimDetailSyncVO syncVO;
    private  String bizIdentityCode;
    private Context context;
    private Map<String,String> hintMap;
}
