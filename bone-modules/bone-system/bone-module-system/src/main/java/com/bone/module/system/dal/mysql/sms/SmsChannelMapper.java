package com.bone.module.system.dal.mysql.sms;

import com.bone.base.core.pojo.PageResult;
import com.bone.base.mybatis.core.mapper.BaseMapperX;
import com.bone.base.mybatis.core.query.LambdaQueryWrapperX;
import com.bone.module.system.controller.admin.sms.vo.channel.SmsChannelPageReqVO;
import com.bone.module.system.dal.dataobject.sms.SmsChannelDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsChannelMapper extends BaseMapperX<SmsChannelDO> {

    default PageResult<SmsChannelDO> selectPage(SmsChannelPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SmsChannelDO>()
                .likeIfPresent(SmsChannelDO::getSignature, reqVO.getSignature())
                .eqIfPresent(SmsChannelDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(SmsChannelDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(SmsChannelDO::getId));
    }

}
