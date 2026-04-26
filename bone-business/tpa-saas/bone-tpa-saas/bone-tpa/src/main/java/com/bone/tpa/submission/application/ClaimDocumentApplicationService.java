package com.bone.tpa.submission.application;

import com.bone.tpa.sdk.submission.model.ClaimDocument;
import com.bone.tpa.submission.application.converter.ClaimDocumentConverter;
import com.bone.tpa.submission.application.dto.ClaimDocumentDTO;
import com.bone.tpa.submission.domain.service.ClaimDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ClaimDocumentApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ClaimDocumentApplicationService.class);

    private final ClaimDocumentService documentService;
    private final ClaimDocumentConverter documentConverter;

    @Autowired
    public ClaimDocumentApplicationService(ClaimDocumentService documentService, ClaimDocumentConverter documentConverter) {
        this.documentService = documentService;
        this.documentConverter = documentConverter;
    }

    public ClaimDocumentDTO addDocumentToSubmission(ClaimDocumentDTO documentDTO) {
        logger.info("Adding document to claim submission for claimNo: {}", documentDTO.getClaimNo());

        // Enhance OCR for document
        //documentDTO.setOcrEnhanced(true);
        logger.info("OCR enhancement applied for document: {}", documentDTO.getDocName());

        ClaimDocument document = documentConverter.toEntity(documentDTO);
        ClaimDocument addedDocument = documentService.addDocument(document);

        logger.info("Document added successfully for claimNo: {}", addedDocument.getClaimNo());

        return documentConverter.toDTO(addedDocument);
    }

    public ClaimDocumentDTO getClaimDocument(Long id) {
        logger.info("Fetching claim document for id: {}", id);

        ClaimDocument document = documentService.findById(id);

        if (document == null) {
            logger.error("No claim document found for id: {}", id);
        } else {
            logger.info("Claim document fetched successfully for id: {}", id);
        }

        return documentConverter.toDTO(document);
    }

    /**
     * 更新影像件
     *
     * @param document 影像件领域实体
     * @return 更新后的影像件
     */
    public void updateDocument(ClaimDocumentDTO documentDTO) {
        logger.info("Updating claim document with id: {}", documentDTO.getId());
        // 执行更新逻辑，通常会对某些字段进行检查或业务处理
        documentService.updateDocument(documentConverter.toEntity(documentDTO));
    }

    /**
     * 逻辑删除影像件
     *
     * @param documentId 影像件ID
     */
    public void deleteDocument(Long documentId) {
        documentService.deleteDocument(documentId);  // 逻辑删除
    }
}
