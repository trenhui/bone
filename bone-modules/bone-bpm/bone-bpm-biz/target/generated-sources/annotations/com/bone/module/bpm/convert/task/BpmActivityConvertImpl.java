package com.bone.module.bpm.convert.task;

import com.bone.module.bpm.controller.admin.task.vo.activity.BpmActivityRespVO;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.flowable.engine.history.HistoricActivityInstance;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:37+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class BpmActivityConvertImpl implements BpmActivityConvert {

    @Override
    public List<BpmActivityRespVO> convertList(List<HistoricActivityInstance> list) {
        if ( list == null ) {
            return null;
        }

        List<BpmActivityRespVO> list1 = new ArrayList<BpmActivityRespVO>( list.size() );
        for ( HistoricActivityInstance historicActivityInstance : list ) {
            list1.add( convert( historicActivityInstance ) );
        }

        return list1;
    }

    @Override
    public BpmActivityRespVO convert(HistoricActivityInstance bean) {
        if ( bean == null ) {
            return null;
        }

        BpmActivityRespVO bpmActivityRespVO = new BpmActivityRespVO();

        bpmActivityRespVO.setKey( bean.getActivityId() );
        bpmActivityRespVO.setType( bean.getActivityType() );
        if ( bean.getStartTime() != null ) {
            bpmActivityRespVO.setStartTime( LocalDateTime.ofInstant( bean.getStartTime().toInstant(), ZoneId.of( "UTC" ) ) );
        }
        if ( bean.getEndTime() != null ) {
            bpmActivityRespVO.setEndTime( LocalDateTime.ofInstant( bean.getEndTime().toInstant(), ZoneId.of( "UTC" ) ) );
        }
        bpmActivityRespVO.setTaskId( bean.getTaskId() );

        return bpmActivityRespVO;
    }
}
