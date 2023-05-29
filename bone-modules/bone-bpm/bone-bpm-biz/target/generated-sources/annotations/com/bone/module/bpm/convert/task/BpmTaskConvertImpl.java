package com.bone.module.bpm.convert.task;

import com.bone.module.bpm.controller.admin.task.vo.task.BpmTaskDonePageItemRespVO;
import com.bone.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import com.bone.module.bpm.controller.admin.task.vo.task.BpmTaskTodoPageItemRespVO;
import com.bone.module.bpm.dal.dataobject.task.BpmTaskExtDO;
import com.bone.module.system.api.user.dto.AdminUserRespDTO;
import java.time.LocalDateTime;
import java.time.ZoneId;
import javax.annotation.processing.Generated;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:37+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class BpmTaskConvertImpl implements BpmTaskConvert {

    @Override
    public BpmTaskTodoPageItemRespVO convert1(Task bean) {
        if ( bean == null ) {
            return null;
        }

        BpmTaskTodoPageItemRespVO bpmTaskTodoPageItemRespVO = new BpmTaskTodoPageItemRespVO();

        bpmTaskTodoPageItemRespVO.setSuspensionState( convertSuspendedToSuspensionState( bean.isSuspended() ) );
        bpmTaskTodoPageItemRespVO.setId( bean.getId() );
        bpmTaskTodoPageItemRespVO.setName( bean.getName() );

        bpmTaskTodoPageItemRespVO.setClaimTime( bean.getClaimTime()==null?null: LocalDateTime.ofInstant(bean.getClaimTime().toInstant(),ZoneId.systemDefault()) );
        bpmTaskTodoPageItemRespVO.setCreateTime( bean.getCreateTime()==null?null:LocalDateTime.ofInstant(bean.getCreateTime().toInstant(),ZoneId.systemDefault()) );

        return bpmTaskTodoPageItemRespVO;
    }

    @Override
    public BpmTaskDonePageItemRespVO convert2(HistoricTaskInstance bean) {
        if ( bean == null ) {
            return null;
        }

        BpmTaskDonePageItemRespVO bpmTaskDonePageItemRespVO = new BpmTaskDonePageItemRespVO();

        bpmTaskDonePageItemRespVO.setId( bean.getId() );
        bpmTaskDonePageItemRespVO.setName( bean.getName() );
        if ( bean.getClaimTime() != null ) {
            bpmTaskDonePageItemRespVO.setClaimTime( LocalDateTime.ofInstant( bean.getClaimTime().toInstant(), ZoneId.of( "UTC" ) ) );
        }
        if ( bean.getCreateTime() != null ) {
            bpmTaskDonePageItemRespVO.setCreateTime( LocalDateTime.ofInstant( bean.getCreateTime().toInstant(), ZoneId.of( "UTC" ) ) );
        }
        if ( bean.getEndTime() != null ) {
            bpmTaskDonePageItemRespVO.setEndTime( LocalDateTime.ofInstant( bean.getEndTime().toInstant(), ZoneId.of( "UTC" ) ) );
        }
        bpmTaskDonePageItemRespVO.setDurationInMillis( bean.getDurationInMillis() );

        return bpmTaskDonePageItemRespVO;
    }

    @Override
    public BpmTaskTodoPageItemRespVO.ProcessInstance convert(ProcessInstance processInstance, AdminUserRespDTO startUser) {
        if ( processInstance == null && startUser == null ) {
            return null;
        }

        BpmTaskTodoPageItemRespVO.ProcessInstance processInstance1 = new BpmTaskTodoPageItemRespVO.ProcessInstance();

        if ( processInstance != null ) {
            processInstance1.setId( processInstance.getId() );
            processInstance1.setName( processInstance.getName() );
            if ( processInstance.getStartUserId() != null ) {
                processInstance1.setStartUserId( Long.parseLong( processInstance.getStartUserId() ) );
            }
            processInstance1.setProcessDefinitionId( processInstance.getProcessDefinitionId() );
        }
        if ( startUser != null ) {
            processInstance1.setStartUserNickname( startUser.getNickname() );
        }

        return processInstance1;
    }

    @Override
    public BpmTaskRespVO convert3(HistoricTaskInstance bean) {
        if ( bean == null ) {
            return null;
        }

        BpmTaskRespVO bpmTaskRespVO = new BpmTaskRespVO();

        bpmTaskRespVO.setDefinitionKey( bean.getTaskDefinitionKey() );
        bpmTaskRespVO.setId( bean.getId() );
        bpmTaskRespVO.setName( bean.getName() );
        if ( bean.getClaimTime() != null ) {
            bpmTaskRespVO.setClaimTime( LocalDateTime.ofInstant( bean.getClaimTime().toInstant(), ZoneId.of( "UTC" ) ) );
        }
        if ( bean.getCreateTime() != null ) {
            bpmTaskRespVO.setCreateTime( LocalDateTime.ofInstant( bean.getCreateTime().toInstant(), ZoneId.of( "UTC" ) ) );
        }
        if ( bean.getEndTime() != null ) {
            bpmTaskRespVO.setEndTime( LocalDateTime.ofInstant( bean.getEndTime().toInstant(), ZoneId.of( "UTC" ) ) );
        }
        bpmTaskRespVO.setDurationInMillis( bean.getDurationInMillis() );

        return bpmTaskRespVO;
    }

    @Override
    public BpmTaskRespVO.User convert3(AdminUserRespDTO bean) {
        if ( bean == null ) {
            return null;
        }

        BpmTaskRespVO.User user = new BpmTaskRespVO.User();

        user.setId( bean.getId() );
        user.setNickname( bean.getNickname() );
        user.setDeptId( bean.getDeptId() );

        return user;
    }

    @Override
    public void copyTo(BpmTaskExtDO from, BpmTaskDonePageItemRespVO to) {
        if ( from == null ) {
            return;
        }

        to.setName( from.getName() );
        to.setCreateTime( from.getCreateTime() );
        to.setEndTime( from.getEndTime() );
        to.setResult( from.getResult() );
        to.setReason( from.getReason() );
    }

    @Override
    public BpmTaskTodoPageItemRespVO.ProcessInstance convert(HistoricProcessInstance processInstance, AdminUserRespDTO startUser) {
        if ( processInstance == null && startUser == null ) {
            return null;
        }

        BpmTaskTodoPageItemRespVO.ProcessInstance processInstance1 = new BpmTaskTodoPageItemRespVO.ProcessInstance();

        if ( processInstance != null ) {
            processInstance1.setId( processInstance.getId() );
            processInstance1.setName( processInstance.getName() );
            if ( processInstance.getStartUserId() != null ) {
                processInstance1.setStartUserId( Long.parseLong( processInstance.getStartUserId() ) );
            }
            processInstance1.setProcessDefinitionId( processInstance.getProcessDefinitionId() );
        }
        if ( startUser != null ) {
            processInstance1.setStartUserNickname( startUser.getNickname() );
        }

        return processInstance1;
    }
}
