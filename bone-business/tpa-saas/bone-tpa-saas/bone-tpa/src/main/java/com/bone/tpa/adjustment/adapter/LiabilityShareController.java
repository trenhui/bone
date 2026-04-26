package com.bone.tpa.adjustment.adapter;

import com.bone.core.result.Result;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.intelligent.adjustment.dto.CoverageDTO;
import com.bone.tpa.intelligent.adjustment.dto.LiabilityShareDTO;
import com.bone.tpa.intelligent.adjustment.dto.LiabilitySharingRelationDTO;
import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.intelligent.adjustment.dto.request.ShareCreateRequest;
import com.bone.tpa.intelligent.adjustment.dto.request.ShareRelationCreateRequest;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.intelligent.adjustment.service.*;
import com.bone.tpa.sdk.adjustment.enums.PlanStatus;
import com.bone.tpa.intelligent.adjustment.service.CoverageService;
import com.bone.tpa.intelligent.adjustment.service.LiabilityService;
import com.bone.tpa.intelligent.adjustment.service.LiabilityShareService;
import com.bone.tpa.intelligent.adjustment.service.PlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tpa/adjust/liability/share")
@SimpleLog
public class LiabilityShareController {

    @Autowired
    private PlanService planService;

    @Autowired
    private LiabilityShareService liabilityShareService;

    @Autowired
    private CoverageService coverageService;

    @Autowired
    private LiabilityService liabilityService;



    @Autowired
    private LiabilityShareReleationService releationService;
    /**
     * 共保责任配置页面初始化
     * @param policyNo
     * @return
     */
    @GetMapping("/shareConfigPageInit")
    public Result<Map<String,Object>> shareConfigPageInit(@RequestParam("policyNo") String policyNo)  {
        Map<String,Object> rs = new HashMap<>();
        List<PlanDTO> planDTOList =  planService.queryByPolicyNo(policyNo, PlanStatus.DRAFT);
        rs.put("planList",planDTOList);
        List<LiabilityConfig> liabilityConfigList =  liabilityService.queryDraftListByPolicyNo(policyNo);
        rs.put("liabilityConfigList",liabilityConfigList);
        Map<String,LiabilityConfig> liabilityConfigMap = PkListUtil.listToMap(liabilityConfigList,LiabilityConfig::getUuid);
        Map<Long,PlanDTO> planDTOMap = PkListUtil.listToMap(planDTOList, t -> Long.valueOf(t.getId()));
        List<LiabilityShareDTO>  shareCodeDTOList =    liabilityShareService.findDraftListByPolicyNo(policyNo);
        rs.put("shareCodeList",shareCodeDTOList);
        Map<String,LiabilityShareDTO> shareDTOMap = PkListUtil.listToMap(shareCodeDTOList,LiabilityShareDTO::getShareCode);
        List<LiabilitySharingRelationDTO> relationDTOList =  releationService.getDraftListByPolicyNo(policyNo);
        List <Map<String,Object>> relationMapList = PkListUtil.newArrayList();
        rs.put("relationList",relationMapList);



        for(LiabilitySharingRelationDTO relationDTO : relationDTOList){
            Map<String,Object> mp = new HashMap<>();
            relationMapList.add(mp);
            mp.put("id",relationDTO.getId());
            mp.put("shareInfo",shareDTOMap.get(relationDTO.getShareCode()));
            mp.put("planId",relationDTO.getPlanId());
            mp.put("planInfo", planDTOMap.get(relationDTO.getPlanId()) );
            mp.put("shareCode",relationDTO.getShareCode());
            LiabilityConfig liabilityConfig =  liabilityConfigMap.get(relationDTO.getLiabilityUuid());
            if( liabilityConfig != null){
                Long coverageId = Long.valueOf(liabilityConfig.getCoverageId());
                CoverageDTO coverageDTO = PkListUtil.first( coverageService.findByIds(PkListUtil.asList(coverageId)));
                mp.put("coverageDTO",coverageDTO);
                mp.put("liabilityName",liabilityConfig.getLiabilityName());
            }
        }



        return Result.ok(rs);
    }


    /**
     * 创建共保代码
     * @return
     */
    @PostMapping("/createShareCode")
    public Result<Boolean> createShareCode(@RequestBody ShareCreateRequest request){
        liabilityShareService.create(request.getDtoList());
        return  Result.ok(true);
    }

    /**
     * 修改共保额度
     * @param dto
     * @return
     */
    @PostMapping("/modifyShareCodeLimit")
    public Result<Boolean> modifyShareCodeLimit(@RequestBody LiabilityShareDTO dto){
        liabilityShareService.updateLimit(dto);
        return  Result.success(true);
    }


    /**
     * 删除共保代码
     * @param id
     * @return
     */
    @GetMapping("/deleteShareCode")
    public Result<Boolean> deleteShareCode(@RequestParam("id") Long id){
        liabilityShareService.delete(id);
        return  Result.ok(true);
    }

    /**
     * 新增共保关系
     * @param
     * @return
     */
    @PostMapping("/createRelation")
    public Result<Boolean> createRelation(@RequestBody ShareRelationCreateRequest request){
        releationService.create(request.getDtoList());
        return  Result.ok(true);

    }

    /**
     * 删除共保关系
     * @param id
     * @return
     */
    @GetMapping("/deleteRelation")
    public Result<Boolean> deleteRelation(@RequestParam("id") Long id){
        releationService.delete(id);
        return  Result.ok(true);
    }
}
