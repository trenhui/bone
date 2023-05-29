package com.bone.module.bpm.convert.task;

import com.bone.module.bpm.controller.admin.task.vo.instance.BpmProcessInstancePageItemRespVO;
import com.bone.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceRespVO;
import com.bone.module.bpm.dal.dataobject.definition.BpmProcessDefinitionExtDO;
import com.bone.module.bpm.dal.dataobject.task.BpmProcessInstanceExtDO;
import com.bone.module.system.api.user.dto.AdminUserRespDTO;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:37+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class BpmProcessInstanceConvertImpl implements BpmProcessInstanceConvert {

    @Override
    public List<BpmProcessInstancePageItemRespVO> convertList(List<BpmProcessInstanceExtDO> list) {
        if ( list == null ) {
            return null;
        }

        List<BpmProcessInstancePageItemRespVO> list1 = new ArrayList<BpmProcessInstancePageItemRespVO>( list.size() );
        for ( BpmProcessInstanceExtDO bpmProcessInstanceExtDO : list ) {
            list1.add( convert( bpmProcessInstanceExtDO ) );
        }

        return list1;
    }

    @Override
    public BpmProcessInstancePageItemRespVO convert(BpmProcessInstanceExtDO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmProcessInstancePageItemRespVO bpmProcessInstancePageItemRespVO = new BpmProcessInstancePageItemRespVO();

        bpmProcessInstancePageItemRespVO.setId( bean.getProcessInstanceId() );
        bpmProcessInstancePageItemRespVO.setName( bean.getName() );
        bpmProcessInstancePageItemRespVO.setProcessDefinitionId( bean.getProcessDefinitionId() );
        bpmProcessInstancePageItemRespVO.setCategory( bean.getCategory() );
        bpmProcessInstancePageItemRespVO.setStatus( bean.getStatus() );
        bpmProcessInstancePageItemRespVO.setResult( bean.getResult() );
        bpmProcessInstancePageItemRespVO.setCreateTime( bean.getCreateTime() );
        bpmProcessInstancePageItemRespVO.setEndTime( bean.getEndTime() );

        return bpmProcessInstancePageItemRespVO;
    }

    @Override
    public List<BpmProcessInstancePageItemRespVO.Task> convertList2(List<Task> tasks) {
        if ( tasks == null ) {
            return null;
        }

        List<BpmProcessInstancePageItemRespVO.Task> list = new ArrayList<BpmProcessInstancePageItemRespVO.Task>( tasks.size() );
        for ( Task task : tasks ) {
            list.add( taskToTask( task ) );
        }

        return list;
    }

    @Override
    public BpmProcessInstanceRespVO convert2(HistoricProcessInstance bean) {
        if ( bean == null ) {
            return null;
        }

        BpmProcessInstanceRespVO bpmProcessInstanceRespVO = new BpmProcessInstanceRespVO();

        bpmProcessInstanceRespVO.setId( bean.getId() );
        bpmProcessInstanceRespVO.setName( bean.getName() );
        if ( bean.getEndTime() != null ) {
            bpmProcessInstanceRespVO.setEndTime( LocalDateTime.ofInstant( bean.getEndTime().toInstant(), ZoneId.of( "UTC" ) ) );
        }
        bpmProcessInstanceRespVO.setBusinessKey( bean.getBusinessKey() );

        return bpmProcessInstanceRespVO;
    }

    @Override
    public void copyTo(BpmProcessInstanceExtDO from, BpmProcessInstanceRespVO to) {
        if ( from == null ) {
            return;
        }

        to.setName( from.getName() );
        to.setCategory( from.getCategory() );
        to.setStatus( from.getStatus() );
        to.setResult( from.getResult() );
        to.setCreateTime( from.getCreateTime() );
        to.setEndTime( from.getEndTime() );
        if ( to.getFormVariables() != null ) {
            Map<String, Object> map = from.getFormVariables();
            if ( map != null ) {
                to.getFormVariables().clear();
                to.getFormVariables().putAll( map );
            }
            else {
                to.setFormVariables( null );
            }
        }
        else {
            Map<String, Object> map = from.getFormVariables();
            if ( map != null ) {
                to.setFormVariables( new LinkedHashMap<String, Object>( map ) );
            }
        }
    }

    @Override
    public BpmProcessInstanceRespVO.ProcessDefinition convert2(ProcessDefinition bean) {
        if ( bean == null ) {
            return null;
        }

        BpmProcessInstanceRespVO.ProcessDefinition processDefinition = new BpmProcessInstanceRespVO.ProcessDefinition();

        processDefinition.setId( bean.getId() );

        return processDefinition;
    }

    @Override
    public void copyTo(BpmProcessDefinitionExtDO from, BpmProcessInstanceRespVO.ProcessDefinition to) {
        if ( from == null ) {
            return;
        }

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

    @Override
    public BpmProcessInstanceRespVO.User convert2(AdminUserRespDTO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmProcessInstanceRespVO.User user = new BpmProcessInstanceRespVO.User();

        user.setId( bean.getId() );
        user.setNickname( bean.getNickname() );
        user.setDeptId( bean.getDeptId() );

        return user;
    }

    protected BpmProcessInstancePageItemRespVO.Task taskToTask(Task task) {
        if ( task == null ) {
            return null;
        }

        BpmProcessInstancePageItemRespVO.Task task1 = new BpmProcessInstancePageItemRespVO.Task();

        task1.setId( task.getId() );
        task1.setName( task.getName() );

        return task1;
    }
}
