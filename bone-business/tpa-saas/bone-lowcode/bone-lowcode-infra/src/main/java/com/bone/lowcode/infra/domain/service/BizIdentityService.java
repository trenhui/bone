package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.lowcode.infra.application.dto.bizIdentity.ListBizIdentityDTO;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.SysBizIdentityDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.SysBizIdentityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;


@Service
public class BizIdentityService {

    @Autowired
    private SysBizIdentityMapper sysBizIdentityMapper;

    public boolean deleteByCode(String bizIdentityCode) {
        LambdaQueryWrapper<SysBizIdentityDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysBizIdentityDO::getCode, bizIdentityCode)
                .eq(SysBizIdentityDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return sysBizIdentityMapper.delete(wrapper) > 0;
    }

    public Page<SysBizIdentityDO> page(ListBizIdentityDTO dto) {
        LambdaQueryWrapper<SysBizIdentityDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getBizType() != null, SysBizIdentityDO::getBizType, dto.getBizType())
                .like(StringUtils.hasText(dto.getName()), SysBizIdentityDO::getName, dto.getName())
                .eq(dto.getStatus() != null, SysBizIdentityDO::getStatus, dto.getStatus())
                .eq(SysBizIdentityDO::getDeleted, DeletedEnum.UNDELETED.getCode());

        Page<SysBizIdentityDO> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        sysBizIdentityMapper.selectPage(page, wrapper);
        return page;
    }

    public SysBizIdentityDO getDOByCode(String bizIdentityCode) {
        return sysBizIdentityMapper.selectOne(new LambdaQueryWrapper<SysBizIdentityDO>()
                .eq(SysBizIdentityDO::getCode, bizIdentityCode)
                .eq(SysBizIdentityDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }


    public void insert(SysBizIdentityDO sysBizIdentityDO) {
        sysBizIdentityMapper.insert(sysBizIdentityDO);
    }

    public List<SysBizIdentityDO> getAll() {
        LambdaQueryWrapper<SysBizIdentityDO> wrapper = new LambdaQueryWrapper<SysBizIdentityDO>()
                .eq(SysBizIdentityDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return sysBizIdentityMapper.selectList(wrapper);
    }

    public List<SysBizIdentityDO> getByType(Byte bizType) {
        LambdaQueryWrapper<SysBizIdentityDO> wrapper = new LambdaQueryWrapper<SysBizIdentityDO>()
                .eq(SysBizIdentityDO::getBizType, bizType)
                .eq(SysBizIdentityDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return sysBizIdentityMapper.selectList(wrapper);
    }

    public List<SysBizIdentityDO> getByParentCode(String parentCode) {
        LambdaQueryWrapper<SysBizIdentityDO> wrapper = new LambdaQueryWrapper<SysBizIdentityDO>()
                .eq(SysBizIdentityDO::getParentCode, parentCode)
                .eq(SysBizIdentityDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return sysBizIdentityMapper.selectList(wrapper);
    }
}
