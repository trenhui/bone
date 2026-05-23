package com.bone.masterdata.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.cmd.PerformDataQualityCheckCommand;
import com.bone.masterdata.domain.quality.QualityCheck;
import com.bone.masterdata.domain.quality.QualityReport;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.QualityCheckRepository;
import com.bone.masterdata.domain.repository.QualityReportRepository;
import com.bone.masterdata.domain.service.quality.DataQualityService;
import com.bone.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
        name = "PerformDataQualityCheck",
        description = "执行数据质量检查",
        inputSchema = "{\"masterDataEntityId\": \"long\"}",
        outputSchema = "{\"checkId\": \"long\"}",
        idempotent = false,
        cost = 5,
        retryable = true,
        timeout = 120)
@Component
@RequiredArgsConstructor
public class PerformDataQualityCheckHandler {
    private final DataQualityService dataQualityService;
    private final QualityCheckRepository qualityCheckRepository;
    private final QualityReportRepository qualityReportRepository;
    private final MasterDataEntityRepository entityRepository;

    @Transactional
    public Long handle(PerformDataQualityCheckCommand cmd) {
        if (entityRepository.findById(cmd.getMasterDataEntityId()) == null) {
            throw NotFoundException.of("主数据实体不存在");
        }

        Long checkId = DistributedIdGenerator.generateLongId();
        QualityCheck check = dataQualityService.performQualityCheck(checkId, cmd.getMasterDataEntityId());
        qualityCheckRepository.insert(check);

        Long reportId = DistributedIdGenerator.generateLongId();
        String reportData =
                String.format(
                        "{\"checkId\":%d,\"masterDataEntityId\":%d,\"totalRecords\":%d,"
                                + "\"passedRecords\":%d,\"failedRecords\":%d,\"status\":\"%s\"}",
                        check.getId(),
                        check.getMasterDataEntityId(),
                        check.getTotalRecords(),
                        check.getPassedRecords(),
                        check.getFailedRecords(),
                        check.getStatus());
        QualityReport report =
                dataQualityService.generateQualityReport(
                        reportId, check.getId(), reportData, check.getFailedRecords());
        qualityReportRepository.insert(report);

        return reportId;
    }
}
