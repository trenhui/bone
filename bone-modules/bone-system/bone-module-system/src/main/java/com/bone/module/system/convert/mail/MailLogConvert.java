package com.bone.module.system.convert.mail;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.controller.admin.mail.vo.log.MailLogRespVO;
import com.bone.module.system.dal.dataobject.mail.MailLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface MailLogConvert {

    MailLogConvert INSTANCE = Mappers.getMapper(MailLogConvert.class);

    PageResult<MailLogRespVO> convertPage(PageResult<MailLogDO> pageResult);

    MailLogRespVO convert(MailLogDO bean);

}
