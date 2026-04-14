package com.registrarops.service;

import com.registrarops.entity.PolicySetting;
import com.registrarops.repository.PolicySettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Admin-managed policy/config surface. Values are persisted in the
 * {@code policy_settings} table and every change is audited via
 * {@link AuditService}.
 *
 * Validation: the allow-list below is the canonical set of keys the admin
 * surface recognises; unknown keys are rejected. Values are validated against
 * a per-key pattern (integer range / hour range / etc.) so invalid input
 * cannot be persisted.
 */
@Service
public class PolicySettingService {

    /** Canonical key → value-pattern registry. */
    private static final Map<String, Pattern> ALLOWED = new LinkedHashMap<>();
    static {
        ALLOWED.put("orders.payment_timeout_minutes",     Pattern.compile("^[1-9]\\d{0,3}$"));
        ALLOWED.put("orders.refund_window_days",          Pattern.compile("^[1-9]\\d{0,2}$"));
        ALLOWED.put("orders.idempotency_window_minutes",  Pattern.compile("^[1-9]\\d{0,3}$"));
        ALLOWED.put("retry.max_attempts",                 Pattern.compile("^[1-9]$"));
        ALLOWED.put("notifications.quiet_start_hour",     Pattern.compile("^([0-9]|1[0-9]|2[0-3])$"));
        ALLOWED.put("notifications.quiet_end_hour",       Pattern.compile("^([0-9]|1[0-9]|2[0-3])$"));
    }

    private final PolicySettingRepository repository;
    private final AuditService auditService;

    public PolicySettingService(PolicySettingRepository repository,
                                AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public Set<String> allowedKeys() {
        return ALLOWED.keySet();
    }

    public List<PolicySetting> findAll() {
        return repository.findAll();
    }

    public Optional<String> get(String key) {
        return repository.findById(key).map(PolicySetting::getValue);
    }

    public int getInt(String key, int fallback) {
        try {
            return get(key).map(Integer::parseInt).orElse(fallback);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Transactional
    public PolicySetting set(String key, String value, Long actorId, String actorUsername) {
        if (!ALLOWED.containsKey(key)) {
            throw new IllegalArgumentException("Unknown policy key: " + key);
        }
        if (value == null || !ALLOWED.get(key).matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid value for " + key + ": " + value);
        }
        PolicySetting setting = repository.findById(key).orElseGet(() -> {
            PolicySetting fresh = new PolicySetting();
            fresh.setKey(key);
            return fresh;
        });
        String oldValue = setting.getValue();
        setting.setValue(value);
        setting.setUpdatedBy(actorId);
        PolicySetting saved = repository.save(setting);

        // Audited change history (reuses existing AuditService pattern).
        auditService.log(actorId, actorUsername, "POLICY_UPDATED",
                "PolicySetting", null, null,
                "{\"key\":\"" + key
                        + "\",\"old\":\"" + (oldValue == null ? "" : oldValue.replace("\"", "'"))
                        + "\",\"new\":\"" + value.replace("\"", "'") + "\"}",
                null);
        return saved;
    }
}
