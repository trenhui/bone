package com.bone.module.system.dal.mysql.permission;

import com.bone.base.mybatis.core.mapper.BaseMapperX;
import com.bone.base.mybatis.core.query.LambdaQueryWrapperX;
import com.bone.module.system.controller.admin.permission.vo.menu.MenuListReqVO;
import com.bone.module.system.dal.dataobject.permission.MenuDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MenuMapper extends BaseMapperX<MenuDO> {

    default MenuDO selectByParentIdAndName(Long parentId, String name) {
        return selectOne(new LambdaQueryWrapper<MenuDO>().eq(MenuDO::getParentId, parentId)
                .eq(MenuDO::getName, name));
    }

    default Long selectCountByParentId(Long parentId) {
        return selectCount(MenuDO::getParentId, parentId);
    }

    default List<MenuDO> selectList(MenuListReqVO reqVO) {
        return selectList(new LambdaQueryWrapperX<MenuDO>().likeIfPresent(MenuDO::getName, reqVO.getName())
                .eqIfPresent(MenuDO::getStatus, reqVO.getStatus()));
    }

}
