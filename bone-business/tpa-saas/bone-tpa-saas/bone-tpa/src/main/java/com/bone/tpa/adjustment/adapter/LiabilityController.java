package com.bone.tpa.adjustment.adapter;

import com.bone.core.result.Result;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.adjustment.application.LiabilityMappingApplicationService;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.core.redis.RedisLockManage;
import com.bone.tpa.intelligent.adjustment.dto.CoverageDTO;
import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.intelligent.adjustment.dto.request.LiabilityCreateBatchRequest;
import com.bone.tpa.intelligent.adjustment.engine.strategy.LiabilityAdjustmentStrategyFactory;
import com.bone.tpa.intelligent.adjustment.enums.DeductEnum;
import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.dao.impl.PlanRepository;
import com.bone.tpa.intelligent.adjustment.service.CoverageService;
import com.bone.tpa.intelligent.adjustment.service.LiabilityService;
import com.bone.tpa.intelligent.adjustment.service.LiabilityShareService;
import com.bone.tpa.intelligent.adjustment.service.PlanService;
import com.bone.tpa.sdk.adjustment.enums.PlanStatus;
import com.bone.tpa.sdk.adjustment.model.Plan;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tpa/adjust/liability")
@SimpleLog
public class LiabilityController {
    @Autowired
    private LiabilityMappingApplicationService liabilityMappingApplicationService;
    @Autowired
    private  LiabilityService liabilityService;

    @Autowired
    private CoverageService coverageService;

    @Autowired
    private  LiabilityShareService liabilityShareService;
    @Autowired
    private  PlanRepository planMapper;

    @Autowired
    private PlanService planService;

    @Autowired
    private  LiabilityAdjustmentStrategyFactory strategyFactory;


    @Autowired
    private RedisLockManage redisLockManage;


    @PostMapping("/create")
    public Result<Boolean> create(@RequestBody LiabilityCreateBatchRequest request)  {
        liabilityService.checkPolicyConfigStatus(request.getPolicyNo());
        liabilityService.create(request);
        return Result.ok(true);
    }




    @GetMapping("/findById")
    public Result<LiabilityConfig> findById(@RequestParam("id") Long id){
        return Result.ok(liabilityService.findById(id));
    }

    /**
     * 共保日责任初始化
     * @param planId
     * @return
     */
    @GetMapping("/shareingConfigPageInit")
    public Result<Map<String,Object>> shareingConfigPageInit(@RequestParam("planId") Long planId){
        Map<String,Object> rs = new HashMap<>();
        rs.put("shareCodeList",liabilityShareService.findByPlanId(planId));
        return Result.ok(rs);
    }


    /**
     * 部署版本
     * @param planId
     * @return
     */
    @GetMapping("/deployPlan")
    public Result<Boolean>  deployPlan(@RequestParam("planId") Long planId){
        //虽然没有复现成功，但是极少数情况下出现过重复发布导致多个草稿态计划的情况，因此加锁
        String lockKey = "saas-deployPlan-"+planId;
        boolean ret = redisLockManage.tryLock(lockKey, 1800);
        if (!ret) {
            throw new TpaBizException(BizErrorCode.TOO_MANY_REQUESTS);
        }

        try {
            liabilityService.checkPolicyConfigStatus(planId);

            Boolean flag = planService.deployPlan(planId);
            if (flag != null && flag) {
                //发布计划时更新责任映射
                liabilityMappingApplicationService.updateLiabilityMapping(planId);
            }
            return Result.ok(flag);
        } finally {
            redisLockManage.unlock(lockKey);
        }
    }

    /**
     * 获取回滚计划列表
     * @param planCode
     * @return
     */
    @GetMapping("/getBackPlanList")
    public Result<List<PlanDTO>>  getBackPlanList(@RequestParam("planCode") String planCode){
        Criteria<Plan> planCriteria = new Criteria<Plan>();
        planCriteria.eq(Plan::getStatus,planCode);
        planCriteria.in(Plan::getStatus, PkListUtil.asList(PlanStatus.ACTIVE.getCode(),
                PlanStatus.INACTIVE.getCode()  ));
        List<Plan> planList =  planMapper.findByCriteria(planCriteria);
        List<PlanDTO> dtoList =   planService.convert(planList);
        return Result.ok(dtoList);
    }
    /**
     * 回滚计划
     * @param planId
     * @return
     */
    @GetMapping("/rollBackPlan")
    public Result<Boolean>  rollBackPlan(@RequestParam("planId") Long planId){
        planService.rollBackFromPlan(planId);
        return Result.ok(true);
    }


    /**
     * 责任的初始化页面
     * @param id
     * @return
     */
    @GetMapping("/liabilityEditPageInit")
    public Result<Map<String,Object>> liabilityEditPageInit(@RequestParam("id")Long id){
        Map<String,Object> rs = new HashMap<>();
        LiabilityConfig config = liabilityService.findById(id);

        if(config == null){
            throw new RuntimeException("责任不存在");
        }
        //检查计划的状态
        planService.checkPlanDraft(config.getPlanId()) ;
        rs.put("liabilityConfig",config);
        CoverageDTO coverageDTO =  coverageService.findById(config.getCoverageId());
        rs.put("coverageDTO",coverageDTO);
        PlanDTO planDTO =  planService.findById(config.getPlanId());
        rs.put("planDTO",planDTO);
        return Result.ok(rs);
    }



    @PostMapping("/saveLiabilityConfig")
    public Result<Boolean> saveLiabilityConfig(@RequestBody LiabilityConfig liabilityConfig){
        liabilityService.checkPolicyConfigStatus(liabilityConfig.getPolicyNo());

        LiabilityConfig configExist =   liabilityService.findById(liabilityConfig.getId());
        if(configExist == null){
            throw new IllegalArgumentException("配置不存在");
        }
        //进行一些判定
        if (!liabilityConfig.getLiabilityType().equals(LiabilityTypeEnum.ALLOWANCE)) {
            if (liabilityConfig.getLiabilityDeduct() != null && liabilityConfig.getLiabilityDeduct().getDeductMode().equals(DeductEnum.DAYS.getCode())) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "只有津贴给付型责任可以设置免赔天数");
            }
        }

        //检查状态
        planService.checkPlanDraft(configExist.getPlanId());
        liabilityConfig.setInvoiceRelateAble(true);
        liabilityConfig.setNextLiabilityType(null);
        liabilityConfig.setNextLiabilityUuid(null);
        liabilityConfig.setLiabilityCode(null);
        liabilityConfig.setCoverageId(null);
        liabilityConfig.setPolicyNo(null);
        liabilityConfig.setPlanId(null);
        liabilityConfig.setTenantId(null);
        liabilityConfig.setVersion(null);
        liabilityConfig.setFormula(strategyFactory.getLiabilityAdjudicationStrategy(liabilityConfig.getLiabilityType()).buildAdjustFormula(liabilityConfig));

        liabilityService.update(liabilityConfig);
        return  Result.ok(Boolean.TRUE);
    }


    /**
     * 构造理算公式
     *
     * @param liabilityConfig 赔案主键
     * @return 理算结果对象
     */
    @PostMapping("/formula")
    public Result<String> buildFormula(@RequestBody LiabilityConfig liabilityConfig) {
        return Result.ok(strategyFactory.getLiabilityAdjudicationStrategy(liabilityConfig.getLiabilityType()).buildAdjustFormula(liabilityConfig));
    }

}
