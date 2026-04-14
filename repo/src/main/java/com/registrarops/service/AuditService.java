package com.registrarops.service;

import com.registrarops.entity.AuditLog;
import com.registrarops.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * APPEND-ONLY audit log writer.
 *
 * The ONLY method that persists data is {@link #log}, which calls
 * {@link AuditLogRepository#save(AuditLog)} with a brand-new entity (id == null).
 * There is intentionally no update or delete in this service — auditing the
 * audit log is a serious anti-pattern and the test suite asserts the
 * repository interface offers no such method.
 *
 * Sensitive field masking (passwords, tokens, phone numbers) is applied to
 * the JSON-serialized old/new values before persistence.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final Pattern PASSWORD_RE = Pattern.compile(
            "(\"(?:password|password_hash|passwordHash|token|secret|api_key|apiKey)\"\\s*:\\s*\")[^\"]*(\")",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PHONE_RE = Pattern.compile(
            "(\"(?:phone|phone_number|phoneNumber|mobile)\"\\s*:\\s*\")[^\"]*(\")",
            Pattern.CASE_INSENSITIVE);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Append a new audit record. Never updates an existing row.
     */
    public AuditLog log(Long actorId,
                        String actorUsername,
                        String action,
                        String entityType,
                        Long entityId,
                        String oldValueJson,
                        String newValueJson,
                        HttpServletRequest request) {
        AuditLog entry = new AuditLog();
        entry.setActorId(actorId);
        entry.setActorUsername(actorUsername);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOldValueMasked(maskSensitive(oldValueJson));
        entry.setNewValueMasked(maskSensitive(newValueJson));
        entry.setIpAddress(request != null ? request.getRemoteAddr() : null);
        entry.setCreatedAt(LocalDateTime.now());
        AuditLog saved = auditLogRepository.save(entry);
        log.debug("audit logged: {} {} {} actor={}", action, entityType, entityId, actorUsername);
        return saved;
    }

    /** Convenience overload for system actions with no HTTP context. */
    public AuditLog logSystem(String action, String entityType, Long entityId, String newValueJson) {
        return log(null, "system", action, entityType, entityId, null, newValueJson, null);
    }

    /**
     * Replace the value of any sensitive JSON field with [MASKED] before persistence.
     * Visible for unit testing.
     */
    public static String maskSensitive(String json) {
        if (json == null || json.isEmpty()) return json;
        String out = PASSWORD_RE.matcher(json).replaceAll("$1[MASKED]$2");
        out = PHONE_RE.matcher(out).replaceAll("$1[MASKED]$2");
        return out;
    }
}
