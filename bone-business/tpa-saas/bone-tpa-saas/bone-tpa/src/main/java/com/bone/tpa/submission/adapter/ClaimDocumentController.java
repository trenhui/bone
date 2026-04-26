package com.bone.tpa.submission.adapter;


import com.bone.core.result.Result;
import com.bone.tpa.submission.application.ClaimDocumentApplicationService;
import com.bone.tpa.submission.application.dto.ClaimDocumentDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/claim-documents")
public class ClaimDocumentController {

    private static final Logger logger = LoggerFactory.getLogger(ClaimDocumentController.class);

    private final ClaimDocumentApplicationService documentService;

    @Autowired
    public ClaimDocumentController(ClaimDocumentApplicationService documentService) {
        this.documentService = documentService;
    }

    /**
     * 创建理赔影像件
     *
     * @param documentDTO 理赔影像件 DTO
     * @return 创建后的影像件信息
     */
    @PostMapping
    public ResponseEntity<Result<ClaimDocumentDTO>> createClaimDocument(@RequestBody ClaimDocumentDTO documentDTO) {
        logger.info("Received request to create claim document for claimNo: {}", documentDTO.getClaimNo());
        try {
            ClaimDocumentDTO createdDocument = documentService.addDocumentToSubmission(documentDTO);
            logger.info("Successfully created claim document for claimNo: {}", documentDTO.getClaimNo());
            return ResponseEntity.ok(Result.ok(createdDocument));
        } catch (Exception e) {
            logger.error("Error creating claim document for claimNo: {}", documentDTO.getClaimNo(), e);
            return ResponseEntity.status(500).body(Result.error("Error creating claim document: " + e.getMessage()));
        }
    }

    /**
     * 获取指定的理赔影像件信息
     *
     * @param documentId 影像件ID
     * @return 影像件信息
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<Result<ClaimDocumentDTO>> getClaimDocument(@PathVariable Long documentId) {
        logger.info("Received request to get claim document for id: {}", documentId);
        try {
            ClaimDocumentDTO documentDTO = documentService.getClaimDocument(documentId);
            if (documentDTO == null) {
                logger.warn("Claim document not found for id: {}", documentId);
                return ResponseEntity.status(404).body(Result.error("Claim document not found"));
            }
            logger.info("Successfully retrieved claim document for id: {}", documentId);
            return ResponseEntity.ok(Result.ok(documentDTO));
        } catch (Exception e) {
            logger.error("Error fetching claim document for id: {}", documentId, e);
            return ResponseEntity.status(500).body(Result.error("Error fetching claim document: " + e.getMessage()));
        }
    }

    /**
     * 更新理赔影像件信息
     *
     * @param documentId  影像件ID
     * @param documentDTO 更新的影像件信息
     * @return 更新结果
     */
    @PutMapping("/{documentId}")
    public ResponseEntity<Result<ClaimDocumentDTO>> updateClaimDocument(@PathVariable Long documentId, @RequestBody ClaimDocumentDTO documentDTO) {
        logger.info("Received request to update claim document for id: {}", documentId);
        try {
            documentDTO.setId(documentId);  // 设置ID，确保更新的是正确的文档
            documentService.updateDocument(documentDTO);
            logger.info("Successfully updated claim document for id: {}", documentId);
            return ResponseEntity.ok(Result.ok(documentDTO));
        } catch (Exception e) {
            logger.error("Error updating claim document for id: {}", documentId, e);
            return ResponseEntity.status(500).body(Result.error("Error updating claim document: " + e.getMessage()));
        }
    }

    /**
     * 删除理赔影像件 (逻辑删除)
     *
     * @param documentId 影像件ID
     * @return 删除结果
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Result<String>> deleteClaimDocument(@PathVariable Long documentId) {
        logger.info("Received request to delete claim document for id: {}", documentId);
        try {
            documentService.deleteDocument(documentId);
            logger.info("Successfully deleted claim document for id: {}", documentId);
            return ResponseEntity.ok(Result.ok("Claim document deleted successfully"));
        } catch (Exception e) {
            logger.error("Error deleting claim document for id: {}", documentId, e);
            return ResponseEntity.status(500).body(Result.error("Error deleting claim document: " + e.getMessage()));
        }
    }
}
