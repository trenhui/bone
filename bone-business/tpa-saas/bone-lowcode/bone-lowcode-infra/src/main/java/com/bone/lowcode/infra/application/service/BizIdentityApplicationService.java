package com.bone.lowcode.infra.application.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.lowcode.infra.application.convert.BizIdentityConvert;
import com.bone.lowcode.infra.application.dto.bizIdentity.ListBizIdentityDTO;
import com.bone.lowcode.infra.application.vo.PageResult;
import com.bone.lowcode.infra.application.vo.bizIdentity.BizIdentityVO;
import com.bone.lowcode.infra.application.vo.bizIdentity.GetAllBizIdentityVo;
import com.bone.lowcode.infra.application.vo.bizIdentity.ZfObjectPageVO;
import com.bone.lowcode.infra.domain.service.BizIdentityService;
import com.bone.lowcode.infra.domain.service.PageService;
import com.bone.lowcode.infra.domain.valueobject.BasicPageCodeEnum;
import com.bone.lowcode.infra.infrastructure.feign.ZfPolicyServerClient;
import com.bone.lowcode.infra.infrastructure.feign.bean.ZfBranchCompany;
import com.bone.lowcode.infra.infrastructure.feign.bean.ZfInsuranceCompany;
import com.bone.lowcode.infra.infrastructure.feign.bean.ZfSlipInfo;
import com.bone.lowcode.infra.infrastructure.feign.bean.ZfToubaoCompany;
import com.bone.lowcode.infra.infrastructure.feign.request.ZfResult;
import com.bone.lowcode.infra.infrastructure.feign.util.ZfPolicyServerUtil;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgPageDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.SysBizIdentityDO;
import com.bone.core.util.PkListUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BizIdentityApplicationService {

    @Autowired
    private BizIdentityService bizIdentityService;

    @Resource
    private ZfPolicyServerClient zfPolicyServerClient;

    @Autowired
    private PageService pageService;

    @Autowired
    private ZfPolicyServerUtil zfPolicyServerUtil;

    public Map<String, Boolean> checkIsCreatedByPageCode(String bizIdentityCode) {
        Map<String, Boolean> res = new HashMap<>();
        List<String> basicPageCodeList = BasicPageCodeEnum.getAllBasicPageCode();
        for (String pageCode : basicPageCodeList) {
            CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
            if (pageDO != null) {
                res.put(pageCode, true);
            } else {
                res.put(pageCode, false);
            }
        }
        return res;
    }

    public PageResult<GetAllBizIdentityVo> list(ListBizIdentityDTO dto) {
        Page<SysBizIdentityDO> page = bizIdentityService.page(dto);
        List<SysBizIdentityDO> rows = page.getRecords();

        List<CfgPageDO> pageDOList = pageService.getIdentityCode();
        Set<String> identityCodeSet = pageDOList.stream().map(CfgPageDO::getBizIdentityCode).collect(Collectors.toSet());

        List<GetAllBizIdentityVo> list = rows.stream().map(identity -> {
            GetAllBizIdentityVo vo = BizIdentityConvert.bizIdentityDoToVo(identity);
            vo.setCreatePage(identityCodeSet.contains(identity.getCode()));
            return vo;
        }).toList();
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), list);
    }

    /**
     * 业务主体类型，1：保险公司，2：保险公司分公司，3：投保公司，4：保险公司分公司（保单号）
     */
    public List<ZfObjectPageVO> getByParam(Integer bizType, String parentCode, String parentName, String nameLike) {
        List<ZfObjectPageVO> rs = PkListUtil.newArrayList();

        if (bizType == 1) {
            //保险公司
            ZfResult<List<ZfInsuranceCompany>> result = zfPolicyServerUtil.getTopInsuranceCompanyPage(nameLike);
            List<ZfInsuranceCompany> list = result.getData();
            if (PkListUtil.isEmpty(list)) {
                return rs;
            }
            for (ZfInsuranceCompany zfInsuranceCompany : list) {
                ZfObjectPageVO vo = new ZfObjectPageVO();
                vo.setBizType(1);
                vo.setName(zfInsuranceCompany.getTopInsuranceName());
                vo.setCode(zfInsuranceCompany.getTopInsuranceCode());
                rs.add(vo);
            }
            return rs;
        }

        if (bizType == 2) {
            //保险公司分公司
            if (StringUtils.isBlank(parentCode)) {
                throw new RuntimeException("保险公司code是空");
            }

            ZfResult<List<ZfBranchCompany>> result = zfPolicyServerUtil.getInsuranceBranchPage(nameLike, parentCode);
            List<ZfBranchCompany> list = result.getData();
            if (PkListUtil.isEmpty(list)) {
                return rs;
            }

            for (ZfBranchCompany branchCompany : list) {
                ZfObjectPageVO vo = new ZfObjectPageVO();
                vo.setBizType(2);
                vo.setName(branchCompany.getInsuName());
                vo.setCode(branchCompany.getInsuCode());
                rs.add(vo);
            }
            return rs;
        }

        if (bizType == 3) {
            //投保公司
            ZfResult<List<ZfToubaoCompany>> result = zfPolicyServerUtil.getToubaoCompanyPage(nameLike);
            List<ZfToubaoCompany> list = result.getData();
            if (PkListUtil.isEmpty(list)) {
                return rs;
            }
            for (ZfToubaoCompany toubaoCompany : list) {
                ZfObjectPageVO vo = new ZfObjectPageVO();
                vo.setBizType(3);
                vo.setName(toubaoCompany.getCorpName());
                vo.setCode(toubaoCompany.getCorpCode());
                rs.add(vo);
            }
            return rs;
        }

        if (bizType == 4) {
            //保险公司分公司（保单号）
            if (StringUtils.isBlank(parentName)) {
                throw new RuntimeException("保险分公司名称是空");
            }

            ZfResult<List<ZfSlipInfo>> result = zfPolicyServerUtil.getSlipInfoPage(nameLike, parentName);
            List<ZfSlipInfo> list = result.getData();
            if (PkListUtil.isEmpty(list)) {
                return rs;
            }
            for (ZfSlipInfo slipInfo : list) {
                ZfObjectPageVO vo = new ZfObjectPageVO();
                vo.setBizType(4);
                vo.setName(slipInfo.getSlipCode());
                vo.setCode(slipInfo.getSlipCode());
                rs.add(vo);
            }
            return rs;
        }

        throw new RuntimeException("bizType not support");
    }

    public List<BizIdentityVO> getAllBizIdentity() {
        List<SysBizIdentityDO> list = bizIdentityService.getAll();
        return list.stream().map(bizIdentity -> {
            BizIdentityVO vo = new BizIdentityVO();
            vo.setBizType(bizIdentity.getBizType());
            vo.setBizName(bizIdentity.getName());
            vo.setBizCode(bizIdentity.getCode());
            vo.setAppCode(bizIdentity.getAppCode());
            return vo;
        }).toList();
    }
}
