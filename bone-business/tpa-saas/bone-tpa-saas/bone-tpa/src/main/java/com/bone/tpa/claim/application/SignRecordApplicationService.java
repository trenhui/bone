package com.bone.tpa.claim.application;

import com.bone.tpa.claim.application.converter.SignRecordConverter;
import com.bone.tpa.claim.application.dto.SignRecordDTO;
import com.bone.tpa.claim.application.request.QueryListRequest;
import com.bone.tpa.claim.domain.service.SignRecordService;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.tpa.sdk.claim.model.SignRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 签收应用层服务
 */
@Service
@Transactional
public class SignRecordApplicationService {
    @Autowired
    private SignRecordConverter signRecordConverter;

    @Autowired
    private SignRecordService signRecordService;

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public SignRecordDTO createSignRecord(SignRecordDTO signRecordDTO) {
        SignRecord signRecord = signRecordConverter.toEntity(signRecordDTO);

        signRecord = signRecordService.createSignRecord(signRecord);

        return signRecordConverter.toDTO(signRecord);
    }

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public Boolean uploadParticipant(Long signId) {
        signRecordService.uploadParticipant(signId);
        return true;
    }

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public List<SignRecordDTO> queryList(QueryListRequest request) {
        List<SignRecord> signRecordList = signRecordService.getByQueryParam(request);

        //转化为DTO
        List<SignRecordDTO> signRecordDTOList = new ArrayList<>();
        for (SignRecord signRecord : signRecordList) {
            SignRecordDTO signRecordDTO = signRecordConverter.toDTO(signRecord);
            signRecordDTOList.add(signRecordDTO);
        }

        return signRecordDTOList;
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public SignRecordDTO getSignDetail(Long signId) {
        SignRecord signRecord = signRecordService.getSignRecord(signId);

        return signRecordConverter.toDTO(signRecord);
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public void confirmSign(Long id) {
        signRecordService.confirmSign(id);
    }


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public void passToNextStage(Long claimId) {
        signRecordService.passToNextStage(claimId);
    }
}
