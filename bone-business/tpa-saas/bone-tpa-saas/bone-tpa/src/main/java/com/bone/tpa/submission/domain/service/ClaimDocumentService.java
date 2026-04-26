package com.bone.tpa.submission.domain.service;

import com.bone.tpa.sdk.submission.model.ClaimDocument;
import com.bone.tpa.submission.domain.repository.ClaimDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ClaimDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(ClaimDocumentService.class);

    private final ClaimDocumentRepository documentRepository;

    @Autowired
    public ClaimDocumentService(ClaimDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public ClaimDocument addDocument(ClaimDocument document) {
        logger.info("Saving claim document to repository for claimNo: {}", document.getClaimNo());
        documentRepository.save(document);
        return document;
    }

    public ClaimDocument findById(Long id) {
        logger.info("Finding claim document by id: {}", id);
        return documentRepository.findById(id);
    }

    /**
     * 更新影像件
     *
     * @param document 影像件领域实体
     * @return 更新后的影像件
     */
    public ClaimDocument updateDocument(ClaimDocument document) {
        logger.info("Updating claim document with id: {}", document.getId());
        // 执行更新逻辑，通常会对某些字段进行检查或业务处理
        documentRepository.save(document);
        return document;
    }

    /**
     * 逻辑删除影像件
     *
     * @param documentId 影像件ID
     */
    public void deleteDocument(Long documentId) {
        documentRepository.deleteById(documentId);  // 保存更改
    }
}
