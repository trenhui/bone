package com.bone.lowcode.infra.application.convert;

import com.bone.lowcode.infra.application.vo.processPage.newSignPage.PageNewSign;
import com.bone.lowcode.infra.application.vo.page.pageJson.PageVO;
import com.bone.lowcode.infra.domain.valueobject.PageItemTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgPageDO;
import lombok.Data;

@Data
public class PageConvert {

    public static PageVO pageDOToPageVO(CfgPageDO pageDO, String displayMode) {
        PageVO pageVO = new PageVO();
        pageVO.setId(pageDO.getId().toString());
        pageVO.setType(PageItemTypeEnum.PAGE.getName());
        pageVO.setDisplayMode(displayMode);
        pageVO.setCode(pageDO.getCode());
        pageVO.setName(pageDO.getName());
        pageVO.setPageType(pageDO.getType());
        return pageVO;
    }

    public static PageNewSign pageDOToPageNewSign(CfgPageDO pageDO, String displayMode) {
        PageNewSign pageVO = new PageNewSign();
        pageVO.setId(pageDO.getId().toString());
        pageVO.setType(PageItemTypeEnum.PAGE.getName());
        pageVO.setDisplayMode(displayMode);
        pageVO.setCode(pageDO.getCode());
        pageVO.setName(pageDO.getName());
        pageVO.setPageType(pageDO.getType());
        return pageVO;
    }
}
