package com.bone.masterdata.infrastructure.config;

import com.bone.masterdata.domain.service.entity.MasterDataEntityService;
import com.bone.masterdata.domain.service.record.MasterDataRecordService;
import com.bone.masterdata.domain.service.quality.DataQualityService;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import com.bone.masterdata.domain.repository.MasterDataRecordRepository;
import com.bone.masterdata.domain.repository.DataQualityRuleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领域服务显式配置
 * 遵循 v1.0 规范：领域服务不允许标注 Spring 注解，在这里统一注册
 */
@Configuration
public class DomainServiceConfiguration {

    @Bean
    public MasterDataEntityService masterDataEntityService(
            MasterDataEntityRepository entityRepository,
            MasterDataFieldRepository fieldRepository
    ) {
        return new MasterDataEntityService(entityRepository, fieldRepository);
    }

    @Bean
    public MasterDataRecordService masterDataRecordService(
            MasterDataRecordRepository recordRepository
    ) {
        return new MasterDataRecordService(recordRepository);
    }

    @Bean
    public DataQualityService dataQualityService(
            DataQualityRuleRepository ruleRepository,
            MasterDataRecordRepository recordRepository
    ) {
        return new DataQualityService(ruleRepository, recordRepository);
    }
}
