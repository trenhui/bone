package com.bone.tpa.intelligent.adjustment.service;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.intelligent.adjustment.converter.CoverageConvert;
import com.bone.tpa.intelligent.adjustment.dto.CoverageDTO;
import com.bone.tpa.sdk.dao.impl.CoverageRepository;
import com.bone.tpa.intelligent.adjustment.util.StringUtil;
import com.bone.tpa.sdk.adjustment.model.Coverage;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CoverageServiceImpl implements CoverageService {
    @Autowired
    private CoverageRepository coverageMapper;

    @Autowired
    private CoverageConvert convert;
    @Autowired
    private PlanService planService;


    @Override
    public List<CoverageDTO> queryByPlanId(Long planId) {
        if(planId == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划id不能为空");
        }
        Criteria<Coverage> criteria= new Criteria<>();
        criteria.addSort(Criteria.getDefaultIdSort());
        criteria.eq(Coverage::getPlanId,planId);
        List<Coverage> list = coverageMapper.findByCriteria(criteria);
        return convert.toDtoList(list);
    }

    @Override
    public CoverageDTO findById(Long id) {
        if(id == null){
            return  null;
        }
        return PkListUtil.first(findByIds(PkListUtil.asList(id)));
    }

    @Override
    public List<CoverageDTO> findByIds(List<Long> ids) {
        if(PkListUtil.isEmpty(ids)){
            return PkListUtil.newArrayList();
        }
        return convert.toDtoList(coverageMapper.findById(ids));
    }

    /**
     * 编辑保存险种
     *
     * @param
     * @return
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void save(List<CoverageDTO> coverageDTOList) {
        if (coverageDTOList.isEmpty()) {
            return;
        }

        //搜索出现存的险种
        List<CoverageDTO> existList = queryByPlanId(coverageDTOList.get(0).getPlanId());

        //整理出id列表
        List<Long> changeIdList = coverageDTOList.stream().map(CoverageDTO::getId).filter(Objects::nonNull).collect(Collectors.toList());

        List<String> existNameList = new ArrayList<>();
        for (CoverageDTO coverageDTO : existList) {
            if (!changeIdList.contains(coverageDTO.getId())) {
                existNameList.add(coverageDTO.getCoverageName());
            }
        }

        List<Coverage> coverageList = convert.toEntityList(coverageDTOList);
        if (coverageList.isEmpty()) {
            return;
        }

        for(Coverage coverage : coverageList){
            if(coverage.getPlanId() == null){
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划不能为空");
            }

            StringUtil.stringRegexCheck(coverage.getCoverageName(),StringUtil.coverageNameMatcher, "请正确输入险种名称，2-20字，中文字母数字下划线");
            StringUtil.stringRegexCheck(coverage.getCoverageCode(),StringUtil.coverageCodeMatcher, "请正确输入险种code，字母数字下划线");

            if(coverage.getCoverageLimit() == null){
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "限额配置不能为空");
            }
            if(coverage.getCoverageLimit().compareTo(BigDecimal.ZERO) == 0) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "险种限额不能为0");
            }

            if (existNameList.contains(coverage.getCoverageName())) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "同保单计划下不允许有重复的险种名称: " + coverage.getCoverageName());
            }
            existNameList.add(coverage.getCoverageName());

            planService.checkPlanDraft(coverage.getPlanId());
            if(coverage.getId() == null){
                //create
                if(coverage.getPolicyNo() == null){
                    throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "所属保单不能为空");
                }
                coverageMapper.insert(coverage);
            }else{
                //update,这2是不能更新的字段
                coverage.setPlanId(null);
                coverage.setPolicyNo(null);
                coverageMapper.update(coverage);
            }
        }

    }


    private List<String> existCoverageName(Long planId) {
        Criteria<Coverage> criteria= new Criteria<>();
        criteria.eq(Coverage::getPlanId, planId);

        List<Coverage> coverageList = coverageMapper.findByCriteria(criteria);

        return coverageList.stream().map(Coverage::getCoverageName).collect(Collectors.toList());
    }
}
