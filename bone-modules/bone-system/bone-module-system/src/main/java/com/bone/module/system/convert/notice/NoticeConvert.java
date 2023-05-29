package com.bone.module.system.convert.notice;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.controller.admin.notice.vo.NoticeCreateReqVO;
import com.bone.module.system.controller.admin.notice.vo.NoticeRespVO;
import com.bone.module.system.controller.admin.notice.vo.NoticeUpdateReqVO;
import com.bone.module.system.dal.dataobject.notice.NoticeDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface NoticeConvert {

    NoticeConvert INSTANCE = Mappers.getMapper(NoticeConvert.class);

    PageResult<NoticeRespVO> convertPage(PageResult<NoticeDO> page);

    NoticeRespVO convert(NoticeDO bean);

    NoticeDO convert(NoticeUpdateReqVO bean);

    NoticeDO convert(NoticeCreateReqVO bean);

}
