package com.bone.module.bpm.convert.definition;

import com.bone.module.bpm.controller.admin.definition.vo.rule.BpmTaskAssignRuleCreateReqVO;
import com.bone.module.bpm.controller.admin.definition.vo.rule.BpmTaskAssignRuleRespVO;
import com.bone.module.bpm.controller.admin.definition.vo.rule.BpmTaskAssignRuleUpdateReqVO;
import com.bone.module.bpm.dal.dataobject.definition.BpmTaskAssignRuleDO;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:37+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class BpmTaskAssignRuleConvertImpl implements BpmTaskAssignRuleConvert {

    @Override
    public BpmTaskAssignRuleRespVO convert(BpmTaskAssignRuleDO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmTaskAssignRuleRespVO bpmTaskAssignRuleRespVO = new BpmTaskAssignRuleRespVO();

        bpmTaskAssignRuleRespVO.setType( bean.getType() );
        Set<Long> set = bean.getOptions();
        if ( set != null ) {
            bpmTaskAssignRuleRespVO.setOptions( new LinkedHashSet<Long>( set ) );
        }
        bpmTaskAssignRuleRespVO.setId( bean.getId() );
        bpmTaskAssignRuleRespVO.setModelId( bean.getModelId() );
        bpmTaskAssignRuleRespVO.setProcessDefinitionId( bean.getProcessDefinitionId() );
        bpmTaskAssignRuleRespVO.setTaskDefinitionKey( bean.getTaskDefinitionKey() );

        return bpmTaskAssignRuleRespVO;
    }

    @Override
    public BpmTaskAssignRuleDO convert(BpmTaskAssignRuleCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmTaskAssignRuleDO.BpmTaskAssignRuleDOBuilder bpmTaskAssignRuleDO = BpmTaskAssignRuleDO.builder();

        bpmTaskAssignRuleDO.modelId( bean.getModelId() );
        bpmTaskAssignRuleDO.taskDefinitionKey( bean.getTaskDefinitionKey() );
        bpmTaskAssignRuleDO.type( bean.getType() );
        Set<Long> set = bean.getOptions();
        if ( set != null ) {
            bpmTaskAssignRuleDO.options( new LinkedHashSet<Long>( set ) );
        }

        return bpmTaskAssignRuleDO.build();
    }

    @Override
    public BpmTaskAssignRuleDO convert(BpmTaskAssignRuleUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmTaskAssignRuleDO.BpmTaskAssignRuleDOBuilder bpmTaskAssignRuleDO = BpmTaskAssignRuleDO.builder();

        bpmTaskAssignRuleDO.id( bean.getId() );
        bpmTaskAssignRuleDO.type( bean.getType() );
        Set<Long> set = bean.getOptions();
        if ( set != null ) {
            bpmTaskAssignRuleDO.options( new LinkedHashSet<Long>( set ) );
        }

        return bpmTaskAssignRuleDO.build();
    }

    @Override
    public List<BpmTaskAssignRuleDO> convertList2(List<BpmTaskAssignRuleRespVO> list) {
        if ( list == null ) {
            return null;
        }

        List<BpmTaskAssignRuleDO> list1 = new ArrayList<BpmTaskAssignRuleDO>( list.size() );
        for ( BpmTaskAssignRuleRespVO bpmTaskAssignRuleRespVO : list ) {
            list1.add( bpmTaskAssignRuleRespVOToBpmTaskAssignRuleDO( bpmTaskAssignRuleRespVO ) );
        }

        return list1;
    }

    protected BpmTaskAssignRuleDO bpmTaskAssignRuleRespVOToBpmTaskAssignRuleDO(BpmTaskAssignRuleRespVO bpmTaskAssignRuleRespVO) {
        if ( bpmTaskAssignRuleRespVO == null ) {
            return null;
        }

        BpmTaskAssignRuleDO.BpmTaskAssignRuleDOBuilder bpmTaskAssignRuleDO = BpmTaskAssignRuleDO.builder();

        bpmTaskAssignRuleDO.id( bpmTaskAssignRuleRespVO.getId() );
        bpmTaskAssignRuleDO.modelId( bpmTaskAssignRuleRespVO.getModelId() );
        bpmTaskAssignRuleDO.processDefinitionId( bpmTaskAssignRuleRespVO.getProcessDefinitionId() );
        bpmTaskAssignRuleDO.taskDefinitionKey( bpmTaskAssignRuleRespVO.getTaskDefinitionKey() );
        bpmTaskAssignRuleDO.type( bpmTaskAssignRuleRespVO.getType() );
        Set<Long> set = bpmTaskAssignRuleRespVO.getOptions();
        if ( set != null ) {
            bpmTaskAssignRuleDO.options( new LinkedHashSet<Long>( set ) );
        }

        return bpmTaskAssignRuleDO.build();
    }
}
