package com.bone.module.system.convert.mail;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.controller.admin.mail.vo.account.MailAccountBaseVO;
import com.bone.module.system.controller.admin.mail.vo.account.MailAccountCreateReqVO;
import com.bone.module.system.controller.admin.mail.vo.account.MailAccountRespVO;
import com.bone.module.system.controller.admin.mail.vo.account.MailAccountSimpleRespVO;
import com.bone.module.system.controller.admin.mail.vo.account.MailAccountUpdateReqVO;
import com.bone.module.system.dal.dataobject.mail.MailAccountDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:15+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class MailAccountConvertImpl implements MailAccountConvert {

    @Override
    public MailAccountDO convert(MailAccountCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        MailAccountDO mailAccountDO = new MailAccountDO();

        mailAccountDO.setMail( bean.getMail() );
        mailAccountDO.setUsername( bean.getUsername() );
        mailAccountDO.setPassword( bean.getPassword() );
        mailAccountDO.setHost( bean.getHost() );
        mailAccountDO.setPort( bean.getPort() );
        mailAccountDO.setSslEnable( bean.getSslEnable() );

        return mailAccountDO;
    }

    @Override
    public MailAccountDO convert(MailAccountUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        MailAccountDO mailAccountDO = new MailAccountDO();

        mailAccountDO.setId( bean.getId() );
        mailAccountDO.setMail( bean.getMail() );
        mailAccountDO.setUsername( bean.getUsername() );
        mailAccountDO.setPassword( bean.getPassword() );
        mailAccountDO.setHost( bean.getHost() );
        mailAccountDO.setPort( bean.getPort() );
        mailAccountDO.setSslEnable( bean.getSslEnable() );

        return mailAccountDO;
    }

    @Override
    public MailAccountRespVO convert(MailAccountDO bean) {
        if ( bean == null ) {
            return null;
        }

        MailAccountRespVO mailAccountRespVO = new MailAccountRespVO();

        mailAccountRespVO.setMail( bean.getMail() );
        mailAccountRespVO.setUsername( bean.getUsername() );
        mailAccountRespVO.setPassword( bean.getPassword() );
        mailAccountRespVO.setHost( bean.getHost() );
        mailAccountRespVO.setPort( bean.getPort() );
        mailAccountRespVO.setSslEnable( bean.getSslEnable() );
        mailAccountRespVO.setId( bean.getId() );
        mailAccountRespVO.setCreateTime( bean.getCreateTime() );

        return mailAccountRespVO;
    }

    @Override
    public PageResult<MailAccountBaseVO> convertPage(PageResult<MailAccountDO> pageResult) {
        if ( pageResult == null ) {
            return null;
        }

        PageResult<MailAccountBaseVO> pageResult1 = new PageResult<MailAccountBaseVO>();

        pageResult1.setList( mailAccountDOListToMailAccountBaseVOList( pageResult.getList() ) );
        pageResult1.setTotal( pageResult.getTotal() );

        return pageResult1;
    }

    @Override
    public List<MailAccountSimpleRespVO> convertList02(List<MailAccountDO> list) {
        if ( list == null ) {
            return null;
        }

        List<MailAccountSimpleRespVO> list1 = new ArrayList<MailAccountSimpleRespVO>( list.size() );
        for ( MailAccountDO mailAccountDO : list ) {
            list1.add( mailAccountDOToMailAccountSimpleRespVO( mailAccountDO ) );
        }

        return list1;
    }

    protected List<MailAccountBaseVO> mailAccountDOListToMailAccountBaseVOList(List<MailAccountDO> list) {
        if ( list == null ) {
            return null;
        }

        List<MailAccountBaseVO> list1 = new ArrayList<MailAccountBaseVO>( list.size() );
        for ( MailAccountDO mailAccountDO : list ) {
            list1.add( convert( mailAccountDO ) );
        }

        return list1;
    }

    protected MailAccountSimpleRespVO mailAccountDOToMailAccountSimpleRespVO(MailAccountDO mailAccountDO) {
        if ( mailAccountDO == null ) {
            return null;
        }

        MailAccountSimpleRespVO mailAccountSimpleRespVO = new MailAccountSimpleRespVO();

        mailAccountSimpleRespVO.setId( mailAccountDO.getId() );
        mailAccountSimpleRespVO.setMail( mailAccountDO.getMail() );

        return mailAccountSimpleRespVO;
    }
}
