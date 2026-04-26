package com.bone.tpa.submission.application;

import com.bone.tpa.sdk.submission.model.ClaimSubmission;
import com.bone.tpa.submission.application.converter.ClaimSubmissionConverter;
import com.bone.tpa.submission.application.dto.ClaimSubmissionDTO;
import com.bone.tpa.submission.domain.service.ClaimSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ClaimSubmissionApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ClaimSubmissionApplicationService.class);

    private final ClaimSubmissionService submissionService;
    private final ClaimSubmissionConverter submissionConverter;

    @Autowired
    public ClaimSubmissionApplicationService(ClaimSubmissionService submissionService, ClaimSubmissionConverter submissionConverter) {
        this.submissionService = submissionService;
        this.submissionConverter = submissionConverter;
    }

    public ClaimSubmissionDTO createClaimSubmission(ClaimSubmissionDTO submissionDTO) {
        logger.info("Starting claim submission creation for claimNo: {}", submissionDTO.getClaimNo());

        // Enhance OCR for submitted documents
        submissionDTO.setOcrEnhanced(true);
        logger.info("OCR enhancement applied for claimNo: {}", submissionDTO.getClaimNo());

        // Generate custom form
        submissionDTO.setCustomFormGenerated(true);
        logger.info("Custom form generated for claimNo: {}", submissionDTO.getClaimNo());

        // Convert DTO to domain entity
        ClaimSubmission submission = submissionConverter.toEntity(submissionDTO);

        // Call domain service to handle submission logic
        ClaimSubmission createdSubmission = submissionService.createSubmission(submission);

        logger.info("Claim submission created successfully for claimNo: {}", createdSubmission.getClaimNo());

        // Return converted DTO
        return submissionConverter.toDTO(createdSubmission);
    }

    public ClaimSubmissionDTO getClaimSubmission(Long id) {
        logger.info("Fetching claim submission for id: {}", id);

        ClaimSubmission submission = submissionService.findById(id);

        if (submission == null) {
            logger.error("No claim submission found for id: {}", id);
        } else {
            logger.info("Claim submission fetched successfully for id: {}", id);
        }

        return submissionConverter.toDTO(submission);
    }

    /**
     * 更新理赔提交状态
     *
     * @param submissionId 理赔提交ID
     * @param action 更新的操作
     */
    public void updateClaimSubmissionStatus(Long submissionId, String action) {
        logger.info("Updating claim submission status for id: {} with action: {}", submissionId, action);
        try {
            submissionService.updateClaimSubmissionStatus(submissionId, action);
            logger.info("Claim submission status updated successfully for id: {}", submissionId);
        } catch (Exception e) {
            logger.error("Error updating claim submission status for id: {}", submissionId, e);
            throw new RuntimeException("Failed to update claim submission status: " + e.getMessage());
        }
    }

    /**
     * 逻辑删除理赔提交
     *
     * @param submissionId 理赔提交ID
     */
    public void deleteClaimSubmission(Long submissionId) {
        logger.info("Deleting claim submission for id: {}", submissionId);
        try {
            submissionService.deleteClaimSubmission(submissionId);
            logger.info("Claim submission deleted successfully for id: {}", submissionId);
        } catch (Exception e) {
            logger.error("Error deleting claim submission for id: {}", submissionId, e);
            throw new RuntimeException("Failed to delete claim submission: " + e.getMessage());
        }
    }
}
