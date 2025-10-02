package com.bone.metadata.auth.domain.mapper.dept;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.bone.metadata.auth.application.vo.dept.DeptListReqVO;
import com.bone.metadata.auth.domain.entity.dept.DeptDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Mapper
public interface DeptMapper extends BaseMapper<DeptDO> {

    default List<DeptDO> selectList(DeptListReqVO reqVO) {
        return selectList(new LambdaQueryWrapper<DeptDO>()
                .like(StringUtils.isNotBlank(reqVO.getName()),DeptDO::getName, reqVO.getName())
                .eq(Objects.nonNull(reqVO.getStatus()),DeptDO::getStatus, reqVO.getStatus()));
    }

    default DeptDO selectByParentIdAndName(Long parentId, String name) {
        return selectOne(new LambdaQueryWrapper<DeptDO>().eq(DeptDO::getParentId, parentId).eq(DeptDO::getName, name));
    }

    default Long selectCountByParentId(Long parentId) {
        return selectCount(new LambdaQueryWrapper<DeptDO>().eq(DeptDO::getParentId, parentId));
    }

    default List<DeptDO> selectListByParentId(Collection<Long> parentIds) {
        return selectList(new LambdaQueryWrapper<DeptDO>().eq(DeptDO::getParentId, parentIds));
    }

}
