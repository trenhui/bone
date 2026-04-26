package com.bone.tpa.adjustment.adapter;

import com.bone.core.result.Result;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.intelligent.adjustment.dto.request.ChainLiabilityRequest;
import com.bone.tpa.intelligent.adjustment.enums.NextLiabilityTypeEnum;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.intelligent.adjustment.service.LiabilityService;
import com.bone.tpa.intelligent.adjustment.service.PlanService;
import com.bone.tpa.sdk.adjustment.enums.PlanStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 先后赔付顺序页面
 */
@RestController
@RequestMapping("/tpa/adjust/liability/sort")
@SimpleLog
public class LiabilitySortController {

    @Autowired
    private LiabilityService liabilityService;


    @Autowired
    private PlanService planService;
    @GetMapping("/pageInit")
    public Result<Map<String,Object>> pageInit(@RequestParam("policyNo")String policyNo){
        Map<String,Object> rs = new HashMap<>();
        List<PlanDTO> planDTOList =  planService.queryByPolicyNo(policyNo, PlanStatus.DRAFT);
        rs.put("planList",planDTOList);
        List<LiabilityConfig> liabilityConfigList =  liabilityService.queryDraftListByPolicyNo(policyNo);
        rs.put("liabilityConfigList",liabilityConfigList);

        Map<String,LiabilityConfig> uuidLiabilityMap = PkListUtil.listToMap(liabilityConfigList,LiabilityConfig::getUuid);
        List<LiabilityConfig> configedList = PkListUtil.newArrayList();
        for(LiabilityConfig config : liabilityConfigList){
            if(config.getNextLiabilityUuid() == null){
                continue;
            }
            configedList.add(config);
        }

        configedList = configedList.stream().sorted((t1,t2)->{
            if(!t1.getPlanId().equals(t2.getPlanId())){
                return t1.getPlanId().compareTo(t2.getPlanId());
            }
            if(!t1.getCoverageId().equals(t2.getCoverageId())){
                return t1.getCoverageId().compareTo(t2.getCoverageId());
            }
            return t1.getId().compareTo(t2.getId());
        }).collect(Collectors.toList());

        List<Map<String,Object>> configMapList = PkListUtil.newArrayList();
        rs.put("configedList",configMapList);

        for(LiabilityConfig config : configedList){
            Map<String,Object> mp = new HashMap<>();
            configMapList.add(mp);
            mp.put("id",config.getId());
            mp.put("planId",config.getPlanId());
            mp.put("planName",planService.findById(Long.valueOf(config.getPlanId())).getPlanName());
            mp.put("coverageId",config.getCoverageId());
            mp.put("liabilityName",config.getLiabilityName());
            LiabilityConfig next =   uuidLiabilityMap.get(config.getNextLiabilityUuid());
            if(next!= null){
                mp.put("nextLiabilityUuid",next.getUuid());
                mp.put("nextLiabilityName",next.getLiabilityName());
                mp.put("nextLiabilityType",config.getNextLiabilityType());
            }


        }

        List <Map<String,String>> nextTypeList = PkListUtil.newArrayList();
        rs.put("nextTypeList",nextTypeList);
        Arrays.stream(NextLiabilityTypeEnum.values()).forEach(e->{
            Map<String,String> nextTypeMap = new HashMap<>();
            nextTypeMap.put("key",e.getCode());
            nextTypeMap.put("value",e.getValue());
            nextTypeList.add(nextTypeMap);
        });
       return Result.success(rs);
    }

    /**
     * 保存先后关系
     * @return
     */
    @PostMapping("/saveSortRelations")
    public Result<Boolean> saveSortRelation(@RequestBody ChainLiabilityRequest request){
        liabilityService.saveChainLiability(request);
        return Result.ok(Boolean.TRUE);
    }


    /**
     * 删除先后关系
     * @return
     */
    @GetMapping("/deleteSortRelation")
    public Result<Boolean> deleteSortRelation(@RequestParam("id")Long id){
        liabilityService.deleteChainLiability(id);
        return Result.ok(Boolean.TRUE);
    }
}
