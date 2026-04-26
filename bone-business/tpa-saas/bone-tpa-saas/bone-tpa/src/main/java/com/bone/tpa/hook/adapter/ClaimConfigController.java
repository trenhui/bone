package com.bone.tpa.hook.adapter;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.result.Result;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.core.hook.BizHookFactory;
import com.bone.tpa.sdk.dao.biz.ClaimHookConfigBiz;
import com.bone.tpa.hook.vo.HookConfigSaveRequest;
import com.bone.tpa.sdk.vo.HookPageconfigVO;
import com.bone.tpa.sdk.claim.model.ClaimHookConfig;
import com.bone.tpa.sdk.identityRule.BizidentityHook;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tpa/claim/config")
public class ClaimConfigController {
    @Autowired
    private  ClaimHookConfigBiz hookConfigBiz;
    @Autowired
    private BizHookFactory hookFactory;

    @PostMapping("/saveHookConfig")
    public Result<List<HookPageconfigVO>> saveHookConfig(@RequestBody HookConfigSaveRequest request){


        ClaimHookConfig updto = new ClaimHookConfig();
        updto.setBizIdentityCode(request.getBizIdentityCode());
        for(HookPageconfigVO vo : request.getConfigList()){
            if(StringUtils.isBlank(vo.getBeanType())){
                throw new IllegalArgumentException("beanType不能为空");
            }
            if(StringUtils.isBlank(vo.getBeanName())){
                throw new IllegalArgumentException("beanName不能为空");
            }
            if(vo.getStatus() == null){
                throw new IllegalArgumentException("开关状态不能为空");
            }
        }
        updto.setHookConfig(JSONObject.toJSONString(request.getConfigList()));
        hookConfigBiz.save(updto);
        return getHookList(request.getBizIdentityCode());
    }
    @RequestMapping("/getHookList")
    public Result<List<HookPageconfigVO>> getHookList(@RequestParam("bizIdentityCode")String bizIdentityCode) {
        List<HookPageconfigVO> existList = hookConfigBiz.getHookConfigList(bizIdentityCode);
        Map<String, HookPageconfigVO> existMap = existList.stream().collect(Collectors.toMap(HookPageconfigVO::getBeanName, Function.identity()));
        List<HookPageconfigVO>  dictList =    getBizHookDict();
        for(HookPageconfigVO dict : dictList){
            HookPageconfigVO vo =   existMap.get(dict.getBeanName());
            if(vo != null){
                dict.setStatus(vo.getStatus());
                continue;
            }
        }
        return Result.success(dictList);

    }


    public  List<HookPageconfigVO>  getBizHookDict() {
        List<HookPageconfigVO> list= new ArrayList<>();
        List<BizidentityHook> allHockList =   hookFactory.getHookList().entrySet().stream().map(t->t.getValue()).collect(Collectors.toList());
        allHockList.sort((t1,t2)->t1.getBeanType().compareTo(t2.getBeanType()));
        for(BizidentityHook hook: allHockList){
            list.add(changeToHookPageconfigVO(hook));
        }
        //toTpaHook
        return list;
    }

    private HookPageconfigVO changeToHookPageconfigVO(BizidentityHook vo ){
        HookPageconfigVO rs = new HookPageconfigVO();
        rs.setDomain(vo.getDomain());
        rs.setDomainDesc(vo.getDomainDesc());
        rs.setBeanType(vo.getBeanType());
        rs.setBeanTypeDesc(vo.getBeanTypeDesc());
        rs.setBeanName(vo.getBeanName());
        rs.setBeanDesc(vo.getBeanDesc());
        return rs;
    }

}
