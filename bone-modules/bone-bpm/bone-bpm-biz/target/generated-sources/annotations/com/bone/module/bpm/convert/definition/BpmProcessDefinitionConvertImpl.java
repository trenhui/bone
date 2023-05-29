package com.bone.module.bpm.convert.definition;

import com.bone.module.bpm.controller.admin.definition.vo.process.BpmProcessDefinitionPageItemRespVO;
import com.bone.module.bpm.controller.admin.definition.vo.process.BpmProcessDefinitionRespVO;
import com.bone.module.bpm.dal.dataobject.definition.BpmProcessDefinitionExtDO;
import com.bone.module.bpm.service.definition.dto.BpmProcessDefinitionCreateReqDTO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.flowable.engine.repository.ProcessDefinition;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:36+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class BpmProcessDefinitionConvertImpl implements BpmProcessDefinitionConvert {

    @Override
    public BpmProcessDefinitionPageItemRespVO convert(ProcessDefinition bean) {
        if ( bean == null ) {
            return null;
        }

        BpmProcessDefinitionPageItemRespVO bpmProcessDefinitionPageItemRespVO = new BpmProcessDefinitionPageItemRespVO();

        bpmProcessDefinitionPageItemRespVO.setId( bean.getId() );
        bpmProcessDefinitionPageItemRespVO.setVersion( bean.getVersion() );
        bpmProcessDefinitionPageItemRespVO.setName( bean.getName() );
        bpmProcessDefinitionPageItemRespVO.setDescription( bean.getDescription() );
        bpmProcessDefinitionPageItemRespVO.setCategory( bean.getCategory() );

        return bpmProcessDefinitionPageItemRespVO;
    }

    @Override
    public BpmProcessDefinitionExtDO convert2(BpmProcessDefinitionCreateReqDTO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmProcessDefinitionExtDO.BpmProcessDefinitionExtDOBuilder bpmProcessDefinitionExtDO = BpmProcessDefinitionExtDO.builder();

        bpmProcessDefinitionExtDO.modelId( bean.getModelId() );
        bpmProcessDefinitionExtDO.description( bean.getDescription() );
        bpmProcessDefinitionExtDO.formType( bean.getFormType() );
        bpmProcessDefinitionExtDO.formId( bean.getFormId() );
        bpmProcessDefinitionExtDO.formConf( bean.getFormConf() );
        List<String> list = bean.getFormFields();
        if ( list != null ) {
            bpmProcessDefinitionExtDO.formFields( new ArrayList<String>( list ) );
        }
        bpmProcessDefinitionExtDO.formCustomCreatePath( bean.getFormCustomCreatePath() );
        bpmProcessDefinitionExtDO.formCustomViewPath( bean.getFormCustomViewPath() );

        return bpmProcessDefinitionExtDO.build();
    }

    @Override
    public BpmProcessDefinitionRespVO convert3(ProcessDefinition bean) {
        if ( bean == null ) {
            return null;
        }

        BpmProcessDefinitionRespVO bpmProcessDefinitionRespVO = new BpmProcessDefinitionRespVO();

        bpmProcessDefinitionRespVO.setSuspensionState( convertSuspendedToSuspensionState( bean.isSuspended() ) );
        bpmProcessDefinitionRespVO.setId( bean.getId() );
        bpmProcessDefinitionRespVO.setVersion( bean.getVersion() );
        bpmProcessDefinitionRespVO.setName( bean.getName() );
        bpmProcessDefinitionRespVO.setDescription( bean.getDescription() );
        bpmProcessDefinitionRespVO.setCategory( bean.getCategory() );

        return bpmProcessDefinitionRespVO;
    }

    @Override
    public void copyTo(BpmProcessDefinitionExtDO from, BpmProcessDefinitionRespVO to) {
        if ( from == null ) {
            return;
        }

        to.setDescription( from.getDescription() );
        to.setFormType( from.getFormType() );
        to.setFormId( from.getFormId() );
        to.setFormConf( from.getFormConf() );
        if ( to.getFormFields() != null ) {
            List<String> list = from.getFormFields();
            if ( list != null ) {
                to.getFormFields().clear();
                to.getFormFields().addAll( list );
            }
            else {
                to.setFormFields( null );
            }
        }
        else {
            List<String> list = from.getFormFields();
            if ( list != null ) {
                to.setFormFields( new ArrayList<String>( list ) );
            }
        }
        to.setFormCustomCreatePath( from.getFormCustomCreatePath() );
        to.setFormCustomViewPath( from.getFormCustomViewPath() );
    }
}
