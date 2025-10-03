package com.bone.metadata.auth.domain.mapper.dept;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.metadata.auth.application.vo.dept.PostPageReqVO;
import com.bone.metadata.auth.domain.entity.dept.PostDO;
import com.bone.core.result.PageResult;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Mapper
public interface PostMapper extends BaseMapper<PostDO> {

    default List<PostDO> selectList(Collection<Long> ids, Collection<Integer> statuses) {
        return selectList(new LambdaQueryWrapper<PostDO>()
                .in(CollectionUtils.isNotEmpty(ids),PostDO::getId, ids)
                .in(CollectionUtils.isNotEmpty(statuses),PostDO::getStatus, statuses));
    }

    default PageResult<PostDO> selectPage(PostPageReqVO reqVO) {
        IPage<PostDO> page = new Page<>();
        page.setCurrent(reqVO.getPageNo());
        page.setSize(reqVO.getPageSize());

        selectPage(page, new LambdaQueryWrapper<PostDO>()
                .like(StringUtils.isNotEmpty(reqVO.getCode()),PostDO::getCode, reqVO.getCode())
                .like(StringUtils.isNotEmpty(reqVO.getName()),PostDO::getName, reqVO.getName())
                .eq(Objects.nonNull(reqVO.getStatus()),PostDO::getStatus, reqVO.getStatus())
                .orderByDesc(PostDO::getId));
        return new PageResult<PostDO>(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal());
    }

    default PostDO selectByName(String name) {
        return selectOne(new LambdaQueryWrapper<PostDO>().eq(PostDO::getName, name));
    }

    default PostDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<PostDO>().eq(PostDO::getCode, code));
    }

}
