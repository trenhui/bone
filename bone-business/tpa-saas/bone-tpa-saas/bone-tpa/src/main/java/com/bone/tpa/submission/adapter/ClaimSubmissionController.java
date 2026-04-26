package com.bone.tpa.submission.adapter;

import com.bone.core.result.Result;
import com.bone.tpa.submission.application.ClaimSubmissionApplicationService;
import com.bone.tpa.submission.application.dto.ClaimSubmissionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/claim/submission")
public class ClaimSubmissionController {

    private static final Logger logger = LoggerFactory.getLogger(ClaimSubmissionController.class);

    private final ClaimSubmissionApplicationService claimSubmissionService;

    @Autowired
    public ClaimSubmissionController(ClaimSubmissionApplicationService claimSubmissionService) {
        this.claimSubmissionService = claimSubmissionService;
    }

    /**
     * 创建理赔提交
     *
     * @param claimSubmissionDTO 理赔提交信息
     * @return 创建结果
     */
    @PostMapping
    public Result<ClaimSubmissionDTO> registerClaim(@RequestBody ClaimSubmissionDTO claimSubmissionDTO) {
        logger.info("Received request to create claim submission for claimNo: {}", claimSubmissionDTO.getClaimNo());
        try {
            ClaimSubmissionDTO createdSubmission = claimSubmissionService.createClaimSubmission(claimSubmissionDTO);
            logger.info("Successfully created claim submission for claimNo: {}", claimSubmissionDTO.getClaimNo());
            return Result.ok(createdSubmission);
        } catch (Exception e) {
            logger.error("Failed to create claim submission for claimNo: {}", claimSubmissionDTO.getClaimNo(), e);
            return Result.error("Error creating claim submission: " + e.getMessage());
        }
    }

    /**
     * 获取理赔提交信息
     *
     * @param submissionId 理赔提交ID
     * @return 理赔提交信息
     */
    @GetMapping("/{submissionId}")
    public ResponseEntity<Result<ClaimSubmissionDTO>> getClaimSubmission(@PathVariable Long submissionId) {
        logger.info("Received request to get claim submission for id: {}", submissionId);
        try {
            ClaimSubmissionDTO submissionDTO = claimSubmissionService.getClaimSubmission(submissionId);
            if (submissionDTO == null) {
                logger.warn("Claim submission not found for id: {}", submissionId);
                return ResponseEntity.status(404).body(Result.error("Claim submission not found"));
            }
            logger.info("Successfully retrieved claim submission for id: {}", submissionId);
            return ResponseEntity.ok(Result.ok(submissionDTO));
        } catch (Exception e) {
            logger.error("Error fetching claim submission for id: {}", submissionId, e);
            return ResponseEntity.status(500).body(Result.error("Error fetching claim submission: " + e.getMessage()));
        }
    }

    /**
     * 更新理赔提交状态
     *
     * @param submissionId 理赔提交ID
     * @param action 更新操作
     * @return 更新结果
     */
    @PutMapping("/{submissionId}/status")
    public ResponseEntity<Result<String>> updateClaimSubmissionStatus(@PathVariable Long submissionId, @RequestParam String action) {
        logger.info("Received request to update claim submission status for id: {} with action: {}", submissionId, action);
        try {
            // Assume the action parameter defines specific transitions in submission status.
            claimSubmissionService.updateClaimSubmissionStatus(submissionId, action);
            logger.info("Successfully updated claim submission status for id: {}", submissionId);
            return ResponseEntity.ok(Result.ok("Claim submission status updated successfully"));
        } catch (Exception e) {
            logger.error("Error updating claim submission status for id: {}", submissionId, e);
            return ResponseEntity.status(500).body(Result.error("Error updating claim submission status: " + e.getMessage()));
        }
    }

    /**
     * 删除理赔提交 (逻辑删除)
     *
     * @param submissionId 理赔提交ID
     * @return 删除结果
     */
    @DeleteMapping("/{submissionId}")
    public ResponseEntity<Result<String>> deleteClaimSubmission(@PathVariable Long submissionId) {
        logger.info("Received request to delete claim submission for id: {}", submissionId);
        try {
            claimSubmissionService.deleteClaimSubmission(submissionId);
            logger.info("Successfully deleted claim submission for id: {}", submissionId);
            return ResponseEntity.ok(Result.ok("Claim submission deleted successfully"));
        } catch (Exception e) {
            logger.error("Error deleting claim submission for id: {}", submissionId, e);
            return ResponseEntity.status(500).body(Result.error("Error deleting claim submission: " + e.getMessage()));
        }
    }
}
