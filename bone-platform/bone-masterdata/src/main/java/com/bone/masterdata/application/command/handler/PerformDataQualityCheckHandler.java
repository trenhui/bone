package com.bone.masterdata.application.command.handler;

import com.bone.masterdata.application.command.cmd.PerformDataQualityCheckCmd;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityId;
import com.bone.masterdata.domain.model.quality.QualityCheck;
import com.bone.masterdata.domain.repository.QualityCheckRepository;
import com.bone.masterdata.domain.service.quality.DataQualityService;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PerformDataQualityCheckHandler {
    private final DataQualityService dataQualityService;
    private final QualityCheckRepository qualityCheckRepository;
    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public Long handle(PerformDataQualityCheckCmd cmd) {
        MasterDataEntityId entityId = MasterDataEntityId.of(cmd.getMasterDataEntityId());
        if (entityRepository.findById(entityId) == null) {
            throw new NotFoundException("主数据实体不存在");
        }

        QualityCheck check = dataQualityService.performQualityCheck(entityId);
        return qualityCheckRepository.save(check).getValue();
    }
}
