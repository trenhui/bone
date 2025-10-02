package com.bone.metadata.auth.domain.mapper.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.bone.metadata.auth.application.vo.MenuListReqVO;
import com.bone.metadata.auth.domain.entity.permission.MenuDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;


import java.util.List;
import java.util.Objects;

@Mapper
public interface MenuMapper extends BaseMapper<MenuDO> {

    default MenuDO selectByParentIdAndName(Long parentId, String name) {
        return selectOne(new LambdaQueryWrapper<MenuDO>().eq(MenuDO::getParentId, parentId).eq(MenuDO::getName, name));
    }

    default Long selectCountByParentId(Long parentId) {
        return selectCount(new LambdaQueryWrapper<MenuDO>().eq(MenuDO::getParentId, parentId));
    }

    default List<MenuDO> selectList(MenuListReqVO reqVO) {
        return selectList(new LambdaQueryWrapper<MenuDO>()
                .like(StringUtils.isNotBlank(reqVO.getName()),MenuDO::getName, reqVO.getName())
                .eq(Objects.nonNull(reqVO.getStatus()),MenuDO::getStatus, reqVO.getStatus()));
    }

    default List<MenuDO> selectListByPermission(String permission) {
        return selectList(new LambdaQueryWrapper<MenuDO>().eq(MenuDO::getPermission, permission));
    }
}
