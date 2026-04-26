package com.bone.tpa.intelligent.adjustment.service;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.intelligent.adjustment.converter.LiabilityShareConvert;
import com.bone.tpa.intelligent.adjustment.dto.LiabilityShareDTO;
import com.bone.tpa.intelligent.adjustment.dto.LiabilitySharingRelationDTO;
import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.sdk.dao.impl.LiabilitySharingRepository;
import com.bone.tpa.intelligent.adjustment.util.AdjustUtil;
import com.bone.tpa.sdk.adjustment.model.LiabilitySharing;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LiabilityShareServiceImpl  implements LiabilityShareService {
    @Autowired
    private  LiabilityShareConvert convert;
    @Autowired
    private LiabilitySharingRepository mapper;


    @Autowired
    private AdjustUtil adjustUtil;


    @Autowired
    private PlanService planService;

    @Override
    public List<LiabilityShareDTO> findDraftListByPolicyNo(String policyNo) {
        Criteria<LiabilitySharing> criteria = new Criteria<>();
        criteria.isNull(LiabilitySharing::getVersion);
        criteria.eq(LiabilitySharing::getPolicyNo,policyNo);
        criteria.addSort(Criteria.getDefaultIdSort());
        return toDtoList(mapper.findByCriteria(criteria));
    }

    /**
     * 根据id加载
     *
     * @param id
     * @return
     */
    @Override
    public LiabilityShareDTO findById(Long id) {
        return toDto(mapper.findById(id));
    }

    private LiabilityShareDTO toDto(LiabilitySharing entity){
        if(entity == null){
            return  null;
        }
        return PkListUtil.first(toDtoList(PkListUtil.asList(entity)));
    }


    private List<LiabilityShareDTO> toDtoList(List<LiabilitySharing> entityList){
        if(PkListUtil.isEmpty(entityList)){
            return PkListUtil.newArrayList();
        }
        List<LiabilityShareDTO>  dtoList =  convert.toDtoList(entityList);
        List<PlanDTO>  planDTOList =  planService.findByIdList(entityList.stream().map(LiabilitySharing::getPlanId).collect(Collectors.toList()));
        Map<Long,PlanDTO> planDTOMap = planDTOList.stream().collect(Collectors.toMap(t -> Long.valueOf(t.getId()) ,planDTO -> planDTO));
        for(LiabilityShareDTO dto : dtoList){
            PlanDTO planDTO =    planDTOMap.get(dto.getPlanId());
            if( planDTO != null){
                dto.setPlanName(planDTO.getPlanName());
            }
        }
        return dtoList;
    }


    /**
     * 根据共保代码查询
     *
     * @param planId
     * @param code
     * @return
     */
    @Override
    public LiabilityShareDTO findByCode(Long planId, String code) {
        List<LiabilityShareDTO>  rsList =    findByPlanId(planId);
       return PkListUtil.first(rsList.stream().filter(t->t.getShareCode().equals(code)).collect(Collectors.toList()));
    }

    /**
     * 根据计划id加载
     *
     * @param planId
     * @return
     */
    @Override
    public List<LiabilityShareDTO> findByPlanId(Long planId) {
        Criteria<LiabilitySharing> criteria = new Criteria();
        criteria.eq(LiabilitySharing::getPlanId,planId);
        return toDtoList(mapper.findByCriteria(criteria));
    }

    /**
     * 保存责任编码
     *
     * @param
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void create(List<LiabilityShareDTO> liabilityShareDTOList) {
        List<LiabilitySharing>  entityList =   convert.toEntityList(liabilityShareDTOList);
        for(LiabilitySharing entity : entityList){
            if(entity.getPlanId() == null){
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "共保计划不能为空");
            }
            //
            planService.checkPlanDraft(entity.getPlanId());
            if(entity.getPolicyNo() == null){
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "保单号不能为空");
            }
            if(entity.getShareCode() == null){
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "共保代码不能为空");
            }
            LiabilityShareDTO checkExist =      findByCode(entity.getPlanId(),entity.getShareCode());
            if( checkExist != null){
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "共保代码已存在");
            }
            if(entity.getShareLimit() == null){
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "共保限额不能为空");
            }
            if(entity.getShareLimit().compareTo(BigDecimal.ZERO) <= 0) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "共保限额必须为正数");
            }

            entity.setShareId(entity.getPolicyNo()+"_"+entity.getShareCode());
            entity.setVersion(null);
            mapper.insert(entity);
        }
    }

    /**
     * 更新限额
     * @param dto
     */
    public void updateLimit( LiabilityShareDTO  dto) {
        Long id = Long.valueOf(dto.getId());
        LiabilityShareDTO exist = findById(id);
        if(exist == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "共保编码不存在");
        }
        if(dto.getShareLimit() == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "共保限额不能为空");
        }
        if(dto.getShareLimit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "共保限额必须为正数");
        }
        planService.checkPlanDraft(Long.valueOf(exist.getPlanId()));
        LiabilitySharing updateDto = new LiabilitySharing();
        updateDto.setShareLimit(dto.getShareLimit());
        updateDto.setId(Long.valueOf(exist.getId()));
        mapper.update(updateDto);
    }

    /**
     * 删除共保
     *
     * @param id
     */
    @Override
    public void delete(Long id) {
        LiabilityShareDTO exist = findById(id);
        if(exist == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "共保编码不存在");
        }

        planService.checkPlanDraft(Long.valueOf(exist.getPlanId()));
        List<LiabilitySharingRelationDTO>  existSubList =  SpringContextUtils.getBean(LiabilityShareReleationService.class).getListByPlanId(Long.valueOf(exist.getPlanId()));

        List<LiabilitySharingRelationDTO> currentShareSubList = existSubList.stream().filter(t -> t.getShareCode().equals(exist.getShareCode())).collect(Collectors.toList());

        if(PkListUtil.isNotEmpty(currentShareSubList) ){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "下属还有共保关系，不能删除");

        }
        mapper.deleteById(id);
    }



}
