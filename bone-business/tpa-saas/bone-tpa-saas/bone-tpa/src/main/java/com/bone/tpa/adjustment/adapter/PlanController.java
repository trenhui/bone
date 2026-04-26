package com.bone.tpa.adjustment.adapter;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.intelligent.adjustment.dto.CoverageDTO;
import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.intelligent.adjustment.dto.PolicyDTO;
import com.bone.tpa.intelligent.adjustment.dto.request.CreateCoverageRequest;
import com.bone.tpa.intelligent.adjustment.dto.request.PolicyQueryRequest;
import com.bone.tpa.intelligent.adjustment.dto.request.UpdateCoverageRequest;
import com.bone.tpa.intelligent.adjustment.dto.request.UpdatePlanRequest;
import com.bone.tpa.sdk.adjustment.enums.PolicyConfigStatusEnum;
import com.bone.tpa.sdk.adjustment.model.Policy;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.intelligent.adjustment.service.CoverageService;
import com.bone.tpa.intelligent.adjustment.service.LiabilityService;
import com.bone.tpa.intelligent.adjustment.service.PlanService;
import com.bone.tpa.intelligent.adjustment.service.PolicyService;
import com.bone.tpa.sdk.adjustment.enums.PlanStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tpa/adjust/plan")
@SimpleLog
public class PlanController {
    @Autowired
    private PolicyService policyService;
    @Autowired
    private LiabilityService liabilityService;


    @Autowired
    private PlanService  planService;

    @Autowired
    private CoverageService coverageService;


    /**
     * 切换保单责任配置状态
     */
    @GetMapping("/toggleconfig")
    public Result<Boolean> togglePolicyConfigStatus(@RequestParam(value = "policyNo",required = true)String policyNo) {
        policyService.updatePolicyConfigStatus(policyNo);
        return Result.success(true);
    }


    /**
     * 分页获取保单列表
     * @return
     */
    @PostMapping("/queryPolicyByPage")
    public Result<PageResult<PolicyDTO>> queryPolicyByPage(@RequestBody PolicyQueryRequest planQueryRequest) {
        PageResult<PolicyDTO> pageResult=  policyService.queryPageByCondition(planQueryRequest);
        return  Result.success(pageResult);
    }


    @GetMapping("/queryPlanByPolicy")
    public Result<List<Map<String,Object>>> queryPlanByPolicy(@RequestParam(value = "policyNo",required = true)String policyNo) {
        List<Map<String,Object>> rs = PkListUtil.newArrayList();
        List<PlanDTO>  planDTOList = planService.queryByPolicyNo(policyNo, PlanStatus.DRAFT);
        if(PkListUtil.isEmpty(planDTOList)){
            return Result.success(rs);
        }
        for(PlanDTO planDTO : planDTOList){
            List<CoverageDTO>  coverageDTOList =  coverageService.queryByPlanId(planDTO.getId());
            List<LiabilityConfig> liabilityDTOList =  liabilityService.queryByPlanId(planDTO.getId());
            Map<Long,List<LiabilityConfig>> liabCoverMap = liabilityDTOList.stream().collect(Collectors.groupingBy(LiabilityConfig::getCoverageId));
            if(PkListUtil.isEmpty(coverageDTOList)){
               Map<String,Object> mp = new HashMap<>();
               mp.put("plan",planDTO);
               rs.add(mp);
                continue;
            }
            for(CoverageDTO coverageDTO : coverageDTOList){
                List<LiabilityConfig> localLiablityList =   liabCoverMap.get(coverageDTO.getId());
                if(PkListUtil.isEmpty(localLiablityList)){
                    Map<String,Object> mp = new HashMap<>();
                    mp.put("plan",planDTO);
                    mp.put("coverage",coverageDTO);
                    rs.add(mp);
                }else {
                    for(LiabilityConfig liabilityDTO : localLiablityList) {
                        Map<String, Object> mp = new HashMap<>();
                        mp.put("plan", planDTO);
                        mp.put("coverage", coverageDTO);
                        mp.put("liability", liabilityDTO);
                        rs.add(mp);
                    }
                }
            }



        }
        return   Result.success(rs);
    }

    /**
     * 新增计划
     */
    @PostMapping("/createPlan")
    public Result<PlanDTO> createPlan(@RequestBody PlanDTO request ){
        PlanDTO planDTO = planService.createPlan(request);
        return  Result.success(planDTO);
    }

    @PostMapping("/createPlanList")
    public Result<List< PlanDTO>> createPlanList(@RequestBody List<PlanDTO> request ){
        List<PlanDTO> rs = planService.createPlanList(request);
        return  Result.success(rs);
    }
    /**
     * 修改计划
     * @return
     */
    @PostMapping("/updatePlan")
    public Result<PlanDTO> updatePlan(@RequestBody UpdatePlanRequest request){
        PlanDTO rs = planService.updatePlan(request.getPlanDTO());
        return  Result.success(rs);
    }

    /**
     * 创建险种
     * @return
     */
    @PostMapping("/createCoverage")
    public Result<Boolean> createCoverage(@RequestBody CreateCoverageRequest request){
         coverageService.save(request.getCoverageDTOList());
        return  Result.success(true);
    }

    /**
     * 更新险种
     * @param request
     * @return
     */
    @PostMapping("/updateCoverage")
    public  Result<Boolean> updateCoverage(@RequestBody UpdateCoverageRequest request){
        coverageService.save(PkListUtil.asList(request.getCoverageDTO()));
        return  Result.success(true);
    }


}
