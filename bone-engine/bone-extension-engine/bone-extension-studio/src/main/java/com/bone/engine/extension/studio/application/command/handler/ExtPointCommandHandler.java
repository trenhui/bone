package com.bone.engine.extension.studio.application.command.handler;

import com.bone.core.model.ApiResponse;
import com.bone.engine.extension.studio.application.service.StudioAuditService;
import com.bone.engine.extension.studio.application.service.StudioCommandResponses;
import com.bone.engine.extension.studio.application.service.StudioIdempotencyService;
import com.bone.engine.extension.studio.application.service.StudioIdempotentExecutor;
import com.bone.engine.extension.studio.application.service.StudioVersionSupport;
import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 扩展点写侧。 */
@Component
@RequiredArgsConstructor
public class ExtPointCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(ExtPointCommandHandler.class);
    private static final String PATH_POINTS = "/api/v1/extension/points";

    private final ExtPointRepository extPointRepository;
    private final StudioIdempotentExecutor idempotentExecutor;
    private final StudioAuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ExtPoint saveExtPoint(ExtPoint extPoint) {
        if (extPoint.getVersion() == null) {
            extPoint.setVersion(0);
        }
        return extPointRepository.save(extPoint);
    }

    @Transactional
    public ExtPoint updateExtPoint(Long id, ExtPoint extPoint) {
        return updateExtPoint(id, extPoint, null);
    }

    @Transactional
    public ExtPoint updateExtPoint(Long id, ExtPoint extPoint, Integer expectedVersion) {
        ExtPoint existing = extPointRepository.findById(id);
        if (existing == null) {
            return null;
        }
        StudioVersionSupport.assertExpected(expectedVersion, existing.getVersion());
        existing.setName(extPoint.getName());
        existing.setDescription(extPoint.getDescription());
        existing.setInterfaceName(extPoint.getInterfaceName());
        existing.setDomain(extPoint.getDomain());
        existing.setCategory(extPoint.getCategory());
        existing.setEnabled(extPoint.isEnabled());
        existing.setVersion(StudioVersionSupport.nextVersion(existing.getVersion()));
        extPointRepository.save(existing);
        return existing;
    }

    @Transactional
    public void deleteExtPoint(Long id) {
        extPointRepository.remove(id);
    }

    @Transactional
    public ExtPoint enableExtPoint(Long id, boolean enabled) {
        ExtPoint extPoint = extPointRepository.findById(id);
        if (extPoint == null) {
            return null;
        }
        extPoint.setEnabled(enabled);
        extPointRepository.save(extPoint);
        return extPoint;
    }

    public int scanAndRegisterExtPoints() {
        log.info("扩展点 classpath 扫描暂未接入，返回 0");
        return 0;
    }

    public ResponseEntity<ApiResponse<ExtPoint>> createPoint(String idempotencyKey, ExtPoint body)
            throws JsonProcessingException {
        return createPoint(
                idempotencyKey, body, StudioIdempotencyService.fingerprint(objectMapper.writeValueAsString(body)));
    }

    public ResponseEntity<ApiResponse<ExtPoint>> createPoint(
            String idempotencyKey, ExtPoint body, String requestFingerprint) {
        if (body == null || !hasText(body.getName()) || !hasText(body.getInterfaceName())) {
            return StudioCommandResponses.badRequest("扩展点名称与 interfaceName 不能为空");
        }
        return idempotentExecutor.execute(
                idempotencyKey,
                "POST",
                PATH_POINTS,
                requestFingerprint,
                () -> {
                    ExtPoint saved = saveExtPoint(body);
                    auditService.success("ext_point.create", "ext_point", String.valueOf(saved.getId()));
                    return StudioCommandResponses.created(
                            StudioCommandResponses.resourceLocation("points", saved.getId()),
                            "创建扩展点成功",
                            saved);
                });
    }

    public ResponseEntity<ApiResponse<ExtPoint>> updatePoint(Long id, ExtPoint body, Integer expectedVersion) {
        ExtPoint updated = updateExtPoint(id, body, expectedVersion);
        if (updated == null) {
            return StudioCommandResponses.notFound("扩展点不存在");
        }
        auditService.success("ext_point.update", "ext_point", String.valueOf(id));
        return StudioCommandResponses.ok("更新扩展点成功", updated);
    }

    public ResponseEntity<Void> deletePoint(Long id) {
        deleteExtPoint(id);
        auditService.success("ext_point.delete", "ext_point", String.valueOf(id));
        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<ApiResponse<ExtPoint>> enablePoint(Long id, boolean enabled) {
        ExtPoint updated = enableExtPoint(id, enabled);
        if (updated == null) {
            return StudioCommandResponses.notFound("扩展点不存在");
        }
        auditService.success(
                enabled ? "ext_point.enable" : "ext_point.disable", "ext_point", String.valueOf(id));
        return ResponseEntity.ok(
                ApiResponse.success((enabled ? "启用" : "禁用") + "扩展点成功", updated));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
