package com.bone.metadata.auth.domain.mapper.logger;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bone.metadata.auth.application.enums.logger.LoginResultEnum;
import com.bone.metadata.auth.application.vo.logger.LoginLogPageReqVO;
import com.bone.metadata.auth.domain.entity.logger.LoginLogDO;
import com.bone.core.result.PageResult;
import org.apache.ibatis.annotations.Mapper;

import java.util.Objects;

@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLogDO> {

    default PageResult<LoginLogDO> selectPage(LoginLogPageReqVO reqVO) {
        LambdaQueryWrapper<LoginLogDO> query = new LambdaQueryWrapper<LoginLogDO>()
                .like(StringUtils.isNotBlank(reqVO.getUserIp()),LoginLogDO::getUserIp, reqVO.getUserIp())
                .like(StringUtils.isNotBlank(reqVO.getUsername()),LoginLogDO::getUsername, reqVO.getUsername())
                .between(Objects.nonNull(reqVO.getCreateTime()),LoginLogDO::getCreateTime, reqVO.getCreateTime()[0], reqVO.getCreateTime()[1]);
        if (Boolean.TRUE.equals(reqVO.getStatus())) {
            query.eq(LoginLogDO::getResult, LoginResultEnum.SUCCESS.getResult());
        } else if (Boolean.FALSE.equals(reqVO.getStatus())) {
            query.gt(LoginLogDO::getResult, LoginResultEnum.SUCCESS.getResult());
        }
        query.orderByDesc(LoginLogDO::getId); // 降序
        IPage<LoginLogDO> page = new Page<>();
        page.setCurrent(reqVO.getPageNo());
        page.setSize(reqVO.getPageSize());
        selectPage(page, query);
        return new PageResult<LoginLogDO>(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal());
    }

}
