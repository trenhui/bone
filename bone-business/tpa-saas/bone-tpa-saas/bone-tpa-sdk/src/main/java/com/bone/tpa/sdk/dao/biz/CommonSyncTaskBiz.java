package com.bone.tpa.sdk.dao.biz;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.sdk.claim.model.SyncTask;
import com.bone.tpa.sdk.dao.SyncTaskRespository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class CommonSyncTaskBiz {
    @Resource
    SyncTaskRespository mapper;


    public SyncTask getById(Long id){
        return   mapper.findById(id);
    }

    public Long addTask(String appName, String bizType,
                        String subType, String data,
                        Date fireTime ) {
        SyncTask task = new SyncTask();
        task.setRetryTimes(0);
        task.setStatus(0);
        task.setAppName(appName);
        task.setBizType(bizType);
        task.setSubType(subType);
        task.setData(data);

        if (fireTime == null) {
            task.setFireTime(new Date());
        } else {
            task.setFireTime(fireTime);
        }
        mapper.insert(task);
        log.info("addCommonTask,id:{}",task.getId());
        return task.getId();
    }

    public boolean updateTaskStatus(SyncTask task) {
        SyncTask updateDto = new SyncTask();
        updateDto.setId(task.getId());
        updateDto.setFireTime(task.getFireTime());
        updateDto.setStatus(task.getStatus());
        updateDto.setRetryTimes(task.getRetryTimes());
          mapper.update(updateDto);
          return true;
    }



    public List<SyncTask> waitingExecuteList(String appName, String bizType,
                                                   String subType, Long idAbove,
                                             Date fireTimeBelow) {


        Criteria<SyncTask> criteria = Criteria.create();
        criteria.eq("appName", appName);
        criteria.eq("bizType", bizType);
        criteria.eq("status", 0);
        if (subType != null) {
            criteria.eq("subType", subType);

        }
        if (idAbove != null) {
            criteria.gt("id", idAbove);

        }
        if (fireTimeBelow != null) {
            criteria.lt("fireTime", fireTimeBelow);
        }
        // 使用bone-metadata-sdk的PageResult
        com.bone.core.model.PageResult<SyncTask> pageResult = mapper.pageByCriteria(criteria);
        return  pageResult.getList();
    }
}
