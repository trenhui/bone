package com.pkh.cloud.auth.domain.mapper.permission;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pkh.cloud.auth.application.vo.role.RolePageReqVO;
import com.pkh.cloud.auth.domain.entity.permission.RoleDO;
import com.bone.core.result.PageResult;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> {

    default PageResult<RoleDO> selectPage(RolePageReqVO reqVO) {
        IPage<RoleDO> page = new Page<>();
        page.setCurrent(reqVO.getPageNo());
        page.setSize(reqVO.getPageSize());
        LambdaQueryWrapper<RoleDO> queryWrapper = new LambdaQueryWrapper<RoleDO>()
                .like(StringUtils.isNotBlank(reqVO.getName()), RoleDO::getName, reqVO.getName())
                .like(StringUtils.isNotBlank(reqVO.getCode()), RoleDO::getCode, reqVO.getCode())
                .eq(Objects.nonNull(reqVO.getStatus()), RoleDO::getStatus, reqVO.getStatus());
        if(Objects.nonNull(reqVO.getCreateTime())&&reqVO.getCreateTime().length>0){
            queryWrapper.between(RoleDO::getCreateTime, reqVO.getCreateTime()[0], reqVO.getCreateTime()[1]);
        }
        queryWrapper.orderByDesc(RoleDO::getSort);
        selectPage(page,queryWrapper);
        return new PageResult<RoleDO>(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal());
    }

    default RoleDO selectByName(String name) {
        return selectOne(new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getName, name));
    }

    default RoleDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getCode, code));
    }

    default List<RoleDO> selectListByStatus(@Nullable Collection<Integer> statuses) {
        return selectList(new LambdaQueryWrapper<RoleDO>().in(RoleDO::getStatus, statuses));
    }

}
