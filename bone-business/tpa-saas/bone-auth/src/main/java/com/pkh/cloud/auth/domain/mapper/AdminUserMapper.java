package com.pkh.cloud.auth.domain.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pkh.cloud.auth.application.vo.user.UserPageReqVO;
import com.pkh.cloud.auth.domain.entity.user.AdminUserDO;
import com.bone.core.result.PageResult;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUserDO> {

    default AdminUserDO selectByUsername(String username) {
        return selectOne(new LambdaQueryWrapper<AdminUserDO>().eq(AdminUserDO::getUsername, username));
    }

    default AdminUserDO selectByEmail(String email) {
        return selectOne(new LambdaQueryWrapper<AdminUserDO>().eq(AdminUserDO::getEmail, email));
    }

    default AdminUserDO selectByMobile(String mobile) {
        return selectOne(new LambdaQueryWrapper<AdminUserDO>().eq(AdminUserDO::getUsername, mobile));
    }

    default PageResult<AdminUserDO> selectPage(UserPageReqVO reqVO, Collection<Long> deptIds) {
        IPage<AdminUserDO> page = new Page<>();
        page.setCurrent(reqVO.getPageNo());
        page.setSize(reqVO.getPageSize());
        LambdaQueryWrapper<AdminUserDO> wrapper = new LambdaQueryWrapper<AdminUserDO>()
                .like(StringUtils.isNotBlank(reqVO.getUsername()), AdminUserDO::getUsername, reqVO.getUsername())
                .like(StringUtils.isNotBlank(reqVO.getMobile()), AdminUserDO::getMobile, reqVO.getMobile())
                .eq(Objects.nonNull(reqVO.getStatus()), AdminUserDO::getStatus, reqVO.getStatus())
                .in(!CollectionUtils.isEmpty(deptIds), AdminUserDO::getDeptId, deptIds);
        if(Objects.nonNull(reqVO.getCreateTime())&& reqVO.getCreateTime().length>0){
            wrapper.between( AdminUserDO::getCreateTime, reqVO.getCreateTime()[0], reqVO.getCreateTime()[1]);
        }
        wrapper.orderByDesc(AdminUserDO::getId);
        selectPage(page,wrapper);
        return new PageResult<AdminUserDO>(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal());
    }

    default List<AdminUserDO> selectListByNickname(String nickname) {
        return selectList(new LambdaQueryWrapper<AdminUserDO>().like(AdminUserDO::getNickname, nickname));
    }

    default List<AdminUserDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapper<AdminUserDO>().eq(AdminUserDO::getStatus, status));
    }

    default List<AdminUserDO> selectListByDeptIds(Collection<Long> deptIds) {
        return selectList(new LambdaQueryWrapper<AdminUserDO>().in(AdminUserDO::getDeptId, deptIds));
    }

}
