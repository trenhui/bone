package com.bone.tpa.submission.domain.service;

import com.bone.tpa.sdk.submission.model.ClaimSubmission;
import com.bone.tpa.submission.domain.repository.ClaimSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ClaimSubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(ClaimSubmissionService.class);

    private final ClaimSubmissionRepository submissionRepository;

    @Autowired
    public ClaimSubmissionService(ClaimSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    public ClaimSubmission createSubmission(ClaimSubmission submission) {

        logger.info("Saving claim submission to the repository for claimNo: {}", submission.getClaimNo());

        submissionRepository.save(submission);

        return submission;
    }

    public ClaimSubmission findById(Long id) {
        logger.info("Finding claim submission by id: {}", id);
        return submissionRepository.findById(id);
    }

    /**
     * 更新理赔提交状态
     *
     * @param submissionId 理赔提交ID
     * @param action 执行的操作
     */
    public void updateClaimSubmissionStatus(Long submissionId, String action) {
        ClaimSubmission submission = findById(submissionId);
        logger.info("Updating claim submission status for claimNo: {}", submission.getClaimNo());

        // 根据action参数进行不同的状态更新
        switch (action.toLowerCase()) {
            case "approve":
                submission.setStatus("APPROVED");
                break;
            case "reject":
                submission.setStatus("REJECTED");
                break;
            case "in_progress":
                submission.setStatus("IN_PROGRESS");
                break;
            default:
                logger.error("Unknown action: {}", action);
                throw new IllegalArgumentException("Unknown action: " + action);
        }

        submissionRepository.save(submission);
        logger.info("Claim submission status updated to {} for claimNo: {}", submission.getStatus(), submission.getClaimNo());
    }

    /**
     * 逻辑删除理赔提交
     *
     * @param submissionId 理赔提交ID
     */
    public void deleteClaimSubmission(Long submissionId) {
        logger.info("Marking claim submission as deleted for id: {}", submissionId);
        submissionRepository.deleteById(submissionId);
    }
}
