package com.bone.module.bpm.convert.message;

import com.bone.module.system.api.sms.dto.send.SmsSendSingleToUserReqDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:37+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class BpmMessageConvertImpl implements BpmMessageConvert {

    @Override
    public SmsSendSingleToUserReqDTO convert(Long userId, String templateCode, Map<String, Object> templateParams) {
        if ( userId == null && templateCode == null && templateParams == null ) {
            return null;
        }

        SmsSendSingleToUserReqDTO smsSendSingleToUserReqDTO = new SmsSendSingleToUserReqDTO();

        smsSendSingleToUserReqDTO.setUserId( userId );
        smsSendSingleToUserReqDTO.setTemplateCode( templateCode );
        Map<String, Object> map = templateParams;
        if ( map != null ) {
            smsSendSingleToUserReqDTO.setTemplateParams( new LinkedHashMap<String, Object>( map ) );
        }

        return smsSendSingleToUserReqDTO;
    }
}
