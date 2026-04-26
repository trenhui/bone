package com.bone.tpa.claim.adapter;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.result.Result;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.claim.application.request.*;
import com.bone.tpa.claim.application.response.ClaimDetailObject;
import com.bone.tpa.config.NoLoginUri;
import com.bone.tpa.core.service.ClaimFlowService;
import com.bone.tpa.sdk.claim.enums.ClaimFlowStatus;
import com.bone.tpa.sdk.claim.model.ClaimFlowConfig;
import com.bone.tpa.sdk.dao.biz.ClaimFlowConfigBiz;
import com.bone.tpa.sdk.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tpa/claim/flow")
public class ClaimFlowController {
    @Autowired
    private  ClaimFlowConfigBiz flowConfigBiz;

    @Autowired
    private ClaimFlowService clainFlowService;
    @NoLoginUri
    @GetMapping("/pageInit")
    public Result<Map<String,Object>> flowConfigPageInit(@RequestParam("bizIdentityCode") String bizIdentityCode) {
        Map<String,Object> rs = new HashMap<>();

        ClaimFlowConfigVO configVO = clainFlowService.getClaimFlowConfig(bizIdentityCode, ClaimFlowStatus.Draft);
        rs.put("config",configVO);
        return Result.success(rs);
    }

    /**
     * 保存流程设置
     * @param request
     * @return
     */
    @NoLoginUri
    @PostMapping("/saveFlowConfig")
    public Result<Map<String,Object>> saveFlowConfig(@RequestBody SaveFlowConfigVORequest request){
        checkSaveFlowConfigVORequest(request);
        ClaimFlowConfig exist =   flowConfigBiz.getByIdentityCodeAndStatus(request.getBizIdentityCode(),ClaimFlowStatus.Draft);
        if(exist == null){
            exist = new ClaimFlowConfig();
            exist.setBizIdentityCode(request.getBizIdentityCode());
            exist.setStatus(ClaimFlowStatus.Draft.getCode());
        }
        exist.setFlowConfig(JSONObject.toJSONString(request.getConfig()));
        flowConfigBiz.save(exist);
        return  flowConfigPageInit(request.getBizIdentityCode());
    }

    //SavePreCheckConfigRequest
    @NoLoginUri
    @PostMapping("/savePreCheckConfigVO")
    public Result<Map<String,Object>>  savePreCheckConfigVO(@RequestBody SavePreCheckConfigRequest request){
        Assert.notNull(request.getBizIdentityCode(),"业务标识bizIdentityCode不能为空");
        Assert.notNull(request.getConfig(),"配置不能为空");
        ClaimFlowConfig exist =   flowConfigBiz.getByIdentityCodeAndStatus(request.getBizIdentityCode(),ClaimFlowStatus.Draft);
        if(exist == null){
            exist = new ClaimFlowConfig();
            exist.setBizIdentityCode(request.getBizIdentityCode());
            exist.setStatus(ClaimFlowStatus.Draft.getCode());
        }
        exist.setPreCheckConfig(JSONObject.toJSONString(request.getConfig()));
        flowConfigBiz.save(exist);
        return  flowConfigPageInit(request.getBizIdentityCode());
    }


    @NoLoginUri
    @PostMapping("/saveInputCheckConfigVO")
    public Result<Map<String,Object>>  saveInputCheckConfigVO(@RequestBody SaveInputConfigRequest request){
        Assert.notNull(request.getBizIdentityCode(),"业务标识bizIdentityCode不能为空");
        Assert.notNull(request.getConfig(),"配置不能为空");
        ClaimFlowConfig exist =   flowConfigBiz.getByIdentityCodeAndStatus(request.getBizIdentityCode(),ClaimFlowStatus.Draft);
        if(exist == null){
            exist = new ClaimFlowConfig();
            exist.setBizIdentityCode(request.getBizIdentityCode());
            exist.setStatus(ClaimFlowStatus.Draft.getCode());
        }
        exist.setInputConfig(JSONObject.toJSONString(request.getConfig()));
        flowConfigBiz.save(exist);
        return  flowConfigPageInit(request.getBizIdentityCode());
    }

    @NoLoginUri
    @PostMapping("/saveQualityCheckConfigVO")
    public Result<Map<String,Object>>  saveQualityCheckConfigVO(@RequestBody SaveQualityConfigRequest request){
        Assert.notNull(request.getBizIdentityCode(),"业务标识bizIdentityCode不能为空");
        Assert.notNull(request.getConfig(),"配置不能为空");
        ClaimFlowConfig exist =   flowConfigBiz.getByIdentityCodeAndStatus(request.getBizIdentityCode(),ClaimFlowStatus.Draft);
        if(exist == null){
            exist = new ClaimFlowConfig();
            exist.setBizIdentityCode(request.getBizIdentityCode());
            exist.setStatus(ClaimFlowStatus.Draft.getCode());
        }
        exist.setQualityConfig(JSONObject.toJSONString(request.getConfig()));
        flowConfigBiz.save(exist);
        return  flowConfigPageInit(request.getBizIdentityCode());
    }

    @NoLoginUri
    @PostMapping("/saveApproveConfigVO")
    public Result<Map<String,Object>>  saveApproveConfigVO(@RequestBody SaveApproveConfigRequest request){
        Assert.notNull(request.getBizIdentityCode(),"业务标识bizIdentityCode不能为空");
        Assert.notNull(request.getConfig(),"配置不能为空");
        ClaimFlowConfig exist =   flowConfigBiz.getByIdentityCodeAndStatus(request.getBizIdentityCode(),ClaimFlowStatus.Draft);
        if(exist == null){
            exist = new ClaimFlowConfig();
            exist.setBizIdentityCode(request.getBizIdentityCode());
            exist.setStatus(ClaimFlowStatus.Draft.getCode());
        }
        exist.setApproveConfig(JSONObject.toJSONString(request.getConfig()));
        flowConfigBiz.save(exist);
        return  flowConfigPageInit(request.getBizIdentityCode());
    }

    @NoLoginUri
    @PostMapping("/saveApproveCheckConfigVO")
    public Result<Map<String,Object>>  saveApproveCheckConfigVO(@RequestBody SaveApproveCheckConfigRequest request){
        Assert.notNull(request.getBizIdentityCode(),"业务标识bizIdentityCode不能为空");
        Assert.notNull(request.getConfig(),"配置不能为空");
        ClaimFlowConfig exist =   flowConfigBiz.getByIdentityCodeAndStatus(request.getBizIdentityCode(),ClaimFlowStatus.Draft);
        if(exist == null){
            exist = new ClaimFlowConfig();
            exist.setBizIdentityCode(request.getBizIdentityCode());
            exist.setStatus(ClaimFlowStatus.Draft.getCode());
        }
        exist.setApproveCheckConfig(JSONObject.toJSONString(request.getConfig()));
        flowConfigBiz.save(exist);
        return  flowConfigPageInit(request.getBizIdentityCode());
    }

    @NoLoginUri
    @PostMapping("/deployFlowConfig")
    public  Result<Map<String,Object>> deployFlowConfig(@RequestParam("bizIdentityCode") String bizIdentityCode){
        flowConfigBiz.deploy(bizIdentityCode);
        return  flowConfigPageInit(bizIdentityCode);
    }




    private void checkSaveFlowConfigVORequest(SaveFlowConfigVORequest request){
        Assert.notNull(request.getBizIdentityCode(),"业务标识bizIdentityCode不能为空");
        Assert.notNull(request.getConfig(),"流程配置flowConfig不能为空");
        FlowConfigVO configVO = request.getConfig();
        BaseFlowNodeVO preCheckconfig =  configVO.getPreCheckFlowNode();
        checkFlowNode(preCheckconfig,"初审");
        checkFlowNode(configVO.getInputFlowNode(),"录入");
        checkFlowNode(configVO.getQualityFlowNode(),"质检");
        checkFlowNode(configVO.getApproveFlowNode(),"审核");
        checkFlowNode(configVO.getApproveCheckFlowNode(),"复核");

    }

    private void checkFlowNode(BaseFlowNodeVO nodeVO ,String nodeName ){
        if( nodeVO == null){
            throw new IllegalArgumentException(nodeName+"节点不能为空");
        }
        if(PkListUtil.isEmpty(nodeVO.getJumpTypeList())){
            throw new IllegalArgumentException(nodeName+"节点跳转类型不能为空");
        }
        boolean checkPreCheckChoosen =   checkChoosen(nodeVO.getJumpTypeList());
        if(!checkPreCheckChoosen){
            throw new IllegalArgumentException(nodeName+"节点跳转类型必须选择一个");
        }
    }

    private boolean checkChoosen(List<JumpTypeVO> jumpTypeVOList){
        if(PkListUtil.isEmpty(jumpTypeVOList)){
            return false;
        }
        boolean ret = false;
        for(JumpTypeVO jumpTypeVO : jumpTypeVOList){
            if(jumpTypeVO.getStatus() == 1){
                return true;
            }
        }
        return  ret;
    }
}
