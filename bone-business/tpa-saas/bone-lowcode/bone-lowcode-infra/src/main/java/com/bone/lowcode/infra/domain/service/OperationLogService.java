package com.bone.lowcode.infra.domain.service;

import com.bone.lowcode.infra.infrastructure.persistence.dataobject.OperationLog;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.OperationLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;


@Service
public class OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    public boolean addOperationLog(Long objectId, Byte objectType, String operation, String createBy) {
        OperationLog log = new OperationLog();
        log.setObjectId(objectId);
        log.setObjectType(objectType);
        log.setOperation(operation);
        log.setCreateBy(createBy);
        log.setCreateTime(new Date());
        int count = operationLogMapper.insert(log);
        return count > 0;
    }

    public boolean addOperationLog(Long objectId, Byte objectType, String operation, String createBy, String beforeValue, String afterValue) {
        OperationLog log = new OperationLog();
        log.setObjectId(objectId);
        log.setObjectType(objectType);
        log.setOperation(operation);
        log.setBeforeValue(beforeValue);
        log.setAfterValue(afterValue);
        log.setCreateBy(createBy);
        log.setCreateTime(new Date());
        int count = operationLogMapper.insert(log);
        return count > 0;
    }
}
